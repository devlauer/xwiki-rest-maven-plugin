package de.elnarion.maven.plugin.xwiki.rest;

import static de.elnarion.maven.plugin.xwiki.rest.XWikiRESTMavenPluginConstants.PREFIX;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import de.elnarion.xwiki.rest.model.jaxb.Page;
import de.elnarion.xwiki.rest.model.jaxb.Space;

public abstract class XWikiRESTAbstractMojo extends AbstractMojo  {

	private static final String MIMETYPE_XML = "application/xml";

	private static final String ACCEPT_HEADER = "Accept";

	/** The encoding. */
	@Parameter(defaultValue = "${project.build.sourceEncoding}")
	protected String encoding;

	/** The target file name. */
	@Parameter(property = PREFIX + "user", required = true)
	protected String user;

	/** The target file name. */
	@Parameter(property = PREFIX + "password", required = true)
	protected String password;

	/** The target file name. */
	@Parameter(property = PREFIX + "xwikiRestUrl", required = true)
	protected String xwikiRestUrl;

	/** The target file name. */
	@Parameter(property = PREFIX + "ignoreCertificate", required = false, defaultValue = "true")
	protected boolean ignoreCertificate;

	/** The target file name. */
	@Parameter(property = PREFIX + "spacePath", required = true)
	private String spacePath;

	@Parameter(property = PREFIX + "restPagesPath", required = false, defaultValue="/pages/")
	private String restPagesPath;

	@Parameter(property = PREFIX + "restSpacesPath", required = false, defaultValue="/spaces/")
	private String restSpacesPath;

	@Parameter(property = PREFIX + "restAttachmentsPath", required = false, defaultValue="/attachments/")
	private String restAttachmentsPath;
	
	@Parameter(property = PREFIX + "restDefaultPageName", required = false, defaultValue="WebHome")
	private String restDefaultPageName;
	

	private CloseableHttpClient httpClient;

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getXwikiRestUrl() {
		if (xwikiRestUrl != null && xwikiRestUrl.endsWith("/"))
			xwikiRestUrl = xwikiRestUrl.substring(0, xwikiRestUrl.length() - 1);
		return xwikiRestUrl;
	}

	public void setXwikiRestUrl(String xwikiRestUrl) {
		this.xwikiRestUrl = xwikiRestUrl;
	}

	public boolean isIgnoreCertificate() {
		return ignoreCertificate;
	}

	public void setIgnoreCertificate(boolean ignoreCertificate) {
		this.ignoreCertificate = ignoreCertificate;
	}

	public String getSpacePath() {
		return spacePath;
	}

	public void setSpacePath(String spacePath) {
		this.spacePath = spacePath;
	}

	protected HttpClientContext getHttpClientContext() {
		try {
			URL targetUrl = new URL(getXwikiRestUrl());
			HttpHost targetHost = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getProtocol());
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));

			AuthCache authCache = new BasicAuthCache();
			authCache.put(targetHost, new BasicScheme());

			// Add AuthCache to the execution context
			HttpClientContext context = HttpClientContext.create();
			context.setCredentialsProvider(credsProvider);
			context.setAuthCache(authCache);

			return context;
		} catch (MalformedURLException e) {
			getLog().error("The xwiki rest url is malformed " + e.getMessage());
			getLog().error(e);
		}
		return null;
	}

	protected synchronized CloseableHttpClient getHttpClient() {
		if (httpClient == null) {
			if (ignoreCertificate) {
				try {
					TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
					SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy)
							.build();
					SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
							NoopHostnameVerifier.INSTANCE);

					Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
							.<ConnectionSocketFactory>create().register("https", sslsf)
							.register("http", new PlainConnectionSocketFactory()).build();

					BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(
							socketFactoryRegistry);
					httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).setConnectionManager(connectionManager)
							.build();
				} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
					getLog().warn("Exception during SSLContext creation " + e.getMessage()
							+ " using default client with ssl verification.");
					httpClient = HttpClients.createDefault();
				}
			} else {
				httpClient = HttpClients.createDefault();
			}
		}
		return httpClient;
	}

	protected void closeHTTPClient() {
		if (httpClient != null) {
			try {
				httpClient.close();
			} catch (IOException e) {
				getLog().info(e);
			}
		}
	}

	protected LinkedList<String> getSpacesList(String paramSpacePath) {
		LinkedList<String> linkedSpacesList = new LinkedList<>();
		if (paramSpacePath != null) {
			String[] spaces = paramSpacePath.split("\\.");
			if (spaces != null) {
				Collections.addAll(linkedSpacesList, spaces);
			}
		}
		return linkedSpacesList;
	}

	protected String getRelativeSpacePath(String paramHomePath) {
		String spacePathPointSeparated = paramHomePath;
		if (spacePathPointSeparated != null) {
			if (spacePathPointSeparated.endsWith(".WebHome")) {
				spacePathPointSeparated = spacePathPointSeparated.substring(0,
						spacePathPointSeparated.indexOf(".WebHome"));
			}
			if (spacePathPointSeparated.startsWith("xwiki:"))
				spacePathPointSeparated = spacePathPointSeparated.substring(6, spacePathPointSeparated.length());
			LinkedList<String> spacesList = getSpacesList(spacePathPointSeparated);
			StringBuilder spacesPathBuilder = new StringBuilder();
			for (String space : spacesList) {
				spacesPathBuilder.append(restSpacesPath);
				spacesPathBuilder.append(space);
			}
			return spacesPathBuilder.toString();
		}
		return null;
	}

	protected Space getSpace(boolean paramCreateNonExistingSpaces) {
		LinkedList<String> spacesList = getSpacesList(spacePath);
		StringBuilder spacesPathBuilder = new StringBuilder();
		String parentPath = "";
		Space spaceObject = null;
		for (String space : spacesList) {
			parentPath = spacesPathBuilder.toString();
			spacesPathBuilder.append(restSpacesPath);
			spacesPathBuilder.append(space);
			spaceObject = getSpace(spacesPathBuilder.toString());
			// it is not possible to create a space directly so we need to create a WebHome
			// Page for the space
			if (spaceObject == null && paramCreateNonExistingSpaces && createOrUpdateSpaceWebHomePage(parentPath, space, null)) {
					spaceObject = getSpace(spacesPathBuilder.toString());
			}
			if (spaceObject == null)
				break;
		}
		return spaceObject;
	}

	protected boolean createOrUpdateSpaceWebHomePage(String parentPath, String paramSpaceName, String paramContent) {
		Page newPage = new Page();
		newPage.setName(restDefaultPageName);
		newPage.setCreated(Calendar.getInstance());
		newPage.setModified(Calendar.getInstance());
		newPage.setContent(paramContent);
		String relativePath = parentPath;
		relativePath = relativePath.startsWith("/") ? relativePath : "/" + relativePath; //NOSONAR - needed for URL
		HttpPut put = new HttpPut(
				getXwikiRestUrl() + relativePath + restSpacesPath + paramSpaceName + restPagesPath + restDefaultPageName);
		put.addHeader(ACCEPT_HEADER, MIMETYPE_XML);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			getMarshaller().marshal(newPage, baos);
			put.setEntity(new StringEntity(baos.toString(), ContentType.create(MIMETYPE_XML, "UTF-8")));
			try (final CloseableHttpResponse response = getHttpClient().execute(put, getHttpClientContext())) {
				if (response.getStatusLine().getStatusCode() == 201 || response.getStatusLine().getStatusCode() == 304
						|| response.getStatusLine().getStatusCode() == 202) {
					return true;
				}
			}
		} catch (JAXBException | IOException e) {
			getLog().warn(e);
		}
		return false;
	}

	protected Space getSpace(String paramRelativePath) {
		if (paramRelativePath != null) {
			final HttpGet httpget = createHttpGetRequest(paramRelativePath);
			getLog().info("Executing request " + httpget.getMethod() + " " + httpget.getURI());
			try (final CloseableHttpResponse response = getHttpClient().execute(httpget, getHttpClientContext())) {
				return handleHttpGetResult(paramRelativePath, response);
			} catch (JAXBException | UnsupportedOperationException | IOException e) {
				getLog().error("Exception during space resolving " + e.getMessage());
				getLog().error(e);
			}
		} else {
			getLog().info("No relative path for xwiki space set returning null");
		}
		return null;
	}

	private Space handleHttpGetResult(String paramRelativePath, final CloseableHttpResponse response)
			throws IOException, JAXBException {
		getLog().info(
				response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
		Header contentEncoding = response.getEntity().getContentEncoding();
		String spaceencoding = StandardCharsets.UTF_8.name();
		if (contentEncoding != null && contentEncoding.getValue() != null)
			spaceencoding = contentEncoding.getValue();
		String content = IOUtils.toString(response.getEntity().getContent(), spaceencoding);
		getLog().debug(content);
		if (response.getStatusLine().getStatusCode() == 200) {
			return unmarshalSpaceResult(content);
		} else {
			getLog().info("Space " + paramRelativePath + "could not be found, returning null");
		}
		return null;
	}

	private HttpGet createHttpGetRequest(String paramRelativePath ) {
		String relativePath = paramRelativePath.startsWith("/") ? paramRelativePath : "/" + paramRelativePath; //NOSONAR - needed for URL
		final HttpGet httpget = new HttpGet(getXwikiRestUrl() + relativePath);
		httpget.addHeader(ACCEPT_HEADER, MIMETYPE_XML);
		return httpget;
	}

	private Space unmarshalSpaceResult(String content) throws JAXBException {
		Unmarshaller unmarshaller = getUnmarshaller();
		Object space = unmarshaller.unmarshal(new ByteArrayInputStream(content.getBytes()));
		if (space instanceof Space && ((Space) space).getHome() != null) {
			return (Space) space;
		}
		else
			return null;
	}

	private Unmarshaller getUnmarshaller() throws JAXBException {
		JAXBContext context = getJaxbContext();
		return context.createUnmarshaller();
	}

	private Marshaller getMarshaller() throws JAXBException {
		JAXBContext context = getJaxbContext();
		return context.createMarshaller();
	}

	private JAXBContext getJaxbContext() throws JAXBException {
		return JAXBContext.newInstance("de.elnarion.xwiki.rest.model.jaxb");
	}

	protected boolean addAttachmentToSpacePage(Space paramSpace, File paramAttachment,
			boolean deleteAttachmentBeforeAdding) {
		String relativePath = getRelativeSpacePath(paramSpace.getHome());
		if (deleteAttachmentBeforeAdding) {
			HttpDelete delete = new HttpDelete(getXwikiRestUrl() + relativePath + restPagesPath + restDefaultPageName
					+ restAttachmentsPath + paramAttachment.getName());
			try (final CloseableHttpResponse response = getHttpClient().execute(delete, getHttpClientContext())) {
				//NOSONAR - only use autocloseable
			} catch (IOException e) {
				getLog().warn("Could not delete Attachment before adding " + e.getMessage());
				getLog().warn(e);
			}
		}
		HttpPut put = new HttpPut(getXwikiRestUrl() + relativePath + restPagesPath + restDefaultPageName + restAttachmentsPath
				+ paramAttachment.getName());
		put.addHeader(ACCEPT_HEADER, MIMETYPE_XML);
		try {
			put.setEntity(new ByteArrayEntity(IOUtils.toByteArray(new FileInputStream(paramAttachment))));
			try (final CloseableHttpResponse response = getHttpClient().execute(put, getHttpClientContext())) {
				if (response.getStatusLine().getStatusCode() == 201
						|| response.getStatusLine().getStatusCode() == 202) {
					return true;
				}
			}
		} catch (IOException e) {
			getLog().error("Fileupload did not work " + e.getMessage());
			getLog().error(e);
		}
		return false;
	}

	protected Space createPageWithContent(String paramContent, boolean paramCreatePageIfItDoesNotExist)
			throws MojoExecutionException {
		Space space = getSpace(paramCreatePageIfItDoesNotExist);
		if (space == null) {
			throw new MojoExecutionException("Page could not be created");
		} else {
			String spaceName = space.getName();
			String relativePath = getRelativeSpacePath(space.getHome());
			if (relativePath != null) {
				String parentPath = relativePath.substring(0, relativePath.lastIndexOf(restSpacesPath + spaceName));
				createOrUpdateSpaceWebHomePage(parentPath, spaceName, paramContent);
			} else {
				throw new MojoExecutionException("Pagecontent can not be set ");
			}
		}
		return space;
	}

	public String getRestPagesPath() {
		return restPagesPath;
	}

	public void setRestPagesPath(String restPagesPath) {
		this.restPagesPath = restPagesPath;
	}

	public String getRestSpacesPath() {
		return restSpacesPath;
	}

	public void setRestSpacesPath(String restSpacesPath) {
		this.restSpacesPath = restSpacesPath;
	}

	public String getRestAttachmentsPath() {
		return restAttachmentsPath;
	}

	public void setRestAttachmentsPath(String restAttachmentsPath) {
		this.restAttachmentsPath = restAttachmentsPath;
	}

}
