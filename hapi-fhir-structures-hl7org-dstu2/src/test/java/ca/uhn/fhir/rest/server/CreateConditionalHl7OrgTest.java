package ca.uhn.fhir.rest.server;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.instance.model.IdType;
import org.hl7.fhir.instance.model.Patient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.util.PortUtil;

/**
 * Created by dsotnikov on 2/25/2014.
 */
public class CreateConditionalHl7OrgTest {
	private static CloseableHttpClient ourClient;
	
	private static String ourLastConditionalUrl;
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(CreateConditionalHl7OrgTest.class);
	private static int ourPort;
	private static Server ourServer;
	private static IdType ourLastId;
	private static IdType ourLastIdParam;
	private static boolean ourLastRequestWasSearch;
	private static FhirContext ourCtx = FhirContext.forDstu2Hl7Org();
	
	
	
	@Before
	public void before() {
		ourLastId = null;
		ourLastConditionalUrl = null;
		ourLastIdParam = null;
		ourLastRequestWasSearch = false;
	}

	@Test
	public void testCreateWithConditionalUrl() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient");
		httpPost.addHeader(Constants.HEADER_IF_NONE_EXIST, "Patient?identifier=system%7C001");
		httpPost.setEntity(new StringEntity(ourCtx.newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(201, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("location").getValue());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("content-location").getValue());
		
		assertNull(ourLastId.getValue());
		assertNull(ourLastIdParam);
		assertEquals("Patient?identifier=system%7C001", ourLastConditionalUrl);

	}

	@Test
	public void testCreateWithoutConditionalUrl() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient?_format=true&_pretty=true");
		httpPost.setEntity(new StringEntity(ourCtx.newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(201, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("location").getValue());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("content-location").getValue());
		
		assertNull(ourLastId.getValue());
		assertNull(ourLastIdParam);
		assertNull(ourLastConditionalUrl);

	}

	@Test
	public void testSearchStillWorks() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?_pretty=true");

		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info("Response was:\n{}", responseContent);

		assertTrue(ourLastRequestWasSearch);
		assertNull(ourLastId);
		assertNull(ourLastIdParam);
		assertNull(ourLastConditionalUrl);

	}

	
	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
	}
		
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		PatientProvider patientProvider = new PatientProvider();

		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer(ourCtx);
		servlet.setResourceProviders(patientProvider);
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}
	
	public static class PatientProvider implements IResourceProvider {

		@Override
		public Class<Patient> getResourceType() {
			return Patient.class;
		}

		@Search
		public List<IResource> search(@OptionalParam(name="foo") StringDt theString) {
			ourLastRequestWasSearch = true;
			return new ArrayList<IResource>();
		}
		
		@Create()
		public MethodOutcome createPatient(@ResourceParam Patient thePatient, @ConditionalUrlParam String theConditional, @IdParam IdType theIdParam) {
			ourLastConditionalUrl = theConditional;
			ourLastId = thePatient.getIdElement();
			ourLastIdParam = theIdParam;
			return new MethodOutcome(new IdType("Patient/001/_history/002"));
		}

	}

}
