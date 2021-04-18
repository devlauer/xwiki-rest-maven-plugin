package de.elnarion.maven.plugin.xwiki.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
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

public abstract class XWikiRESTAbstractMojo extends AbstractMojo implements XWikiRESTMavenPluginConstants {

	private static final String ATTACHMENTS_PATH = "/attachments/";

	protected static final String DEFAULT_PAGE_NAME = "WebHome";

	protected static final String PAGES_PATH = "/pages/";

	protected static final String SPACES_PATH = "/spaces/";

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

	@Override
	protected void finalize() throws Throwable {
		if (httpClient != null) {
			httpClient.close();
		}
		super.finalize();
	}

	protected LinkedList<String> getSpacesList(String paramSpacePath) {
		LinkedList<String> linkedSpacesList = new LinkedList<>();
		if (paramSpacePath != null) {
			String[] spaces = paramSpacePath.split("\\.");
			if (spaces != null) {
				for (String space : spaces)
					linkedSpacesList.add(space);
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
			String spacesPath = "";
			for (String space : spacesList) {
				spacesPath = spacesPath + SPACES_PATH + space;
			}
			return spacesPath;
		}
		return null;
	}

	protected Space getSpace(boolean paramCreateNonExistingSpaces) {
		LinkedList<String> spacesList = getSpacesList(spacePath);
		String spacesPath = "";
		String parentPath = "";
		Space spaceObject = null;
		for (String space : spacesList) {
			parentPath = spacesPath;
			spacesPath = spacesPath + SPACES_PATH + space;
			spaceObject = getSpace(spacesPath);
			if (spaceObject == null && paramCreateNonExistingSpaces) {
				// it is not possible to create a space directly so we need to create a WebHome
				// Page for the space
				if (createOrUpdateSpaceWebHomePage(parentPath, space, null)) {
					spaceObject = getSpace(spacesPath);
				}
			}
			if (spaceObject == null)
				break;
		}
		return spaceObject;
	}

	protected boolean createOrUpdateSpaceWebHomePage(String parentPath, String paramSpaceName, String paramContent) {
		Page newPage = new Page();
		newPage.setName(DEFAULT_PAGE_NAME);
		newPage.setCreated(Calendar.getInstance());
		newPage.setModified(Calendar.getInstance());
		newPage.setContent(paramContent);
		String relativePath = parentPath;
		relativePath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
		HttpPut put = new HttpPut(
				getXwikiRestUrl() + relativePath + SPACES_PATH + paramSpaceName + PAGES_PATH + DEFAULT_PAGE_NAME);
		put.addHeader("Accept", "application/xml");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			getMarshaller().marshal(newPage, baos);
			put.setEntity(new StringEntity(baos.toString(), ContentType.create("application/xml", "UTF-8")));
			try (final CloseableHttpResponse response = getHttpClient().execute(put, getHttpClientContext())) {
				if (response.getStatusLine().getStatusCode() == 201 || response.getStatusLine().getStatusCode() == 304
						|| response.getStatusLine().getStatusCode() == 202) {
					return true;
				}
			}
		} catch (JAXBException | IOException e) {

			e.printStackTrace();
		}
		return false;
	}

	protected Space getSpace(String paramRelativePath) {
		String relativePath = paramRelativePath;
		if (relativePath != null) {
			relativePath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
			final HttpGet httpget = new HttpGet(getXwikiRestUrl() + paramRelativePath);
			httpget.addHeader("Accept", "application/xml");
			getLog().info("Executing request " + httpget.getMethod() + " " + httpget.getURI());
			try (final CloseableHttpResponse response = getHttpClient().execute(httpget, getHttpClientContext())) {
				getLog().info(
						response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
				Header contentEncoding = response.getEntity().getContentEncoding();
				String encoding = "UTF-8";
				if (contentEncoding != null && contentEncoding.getValue() != null)
					encoding = contentEncoding.getValue();
				String content = IOUtils.toString(response.getEntity().getContent(), encoding);
				getLog().debug(content);
				if (response.getStatusLine().getStatusCode() == 200) {
					Unmarshaller unmarshaller = getUnmarshaller();
					Object space = unmarshaller.unmarshal(new ByteArrayInputStream(content.getBytes()));
					if (space instanceof Space && ((Space) space).getHome() != null) {
						return (Space) space;
					}
				} else {
					getLog().info("Space " + paramRelativePath + "could not be found, returning null");
				}
			} catch (JAXBException | UnsupportedOperationException | IOException e) {
				getLog().error("Exception during space resolving " + e.getMessage());
				getLog().error(e);
			}
		} else {
			getLog().info("No relative path for xwiki space set returning null");
		}
		return null;
	}

	private Unmarshaller getUnmarshaller() throws JAXBException {
		JAXBContext context = getJaxbContext();
		Unmarshaller unmarshaller = context.createUnmarshaller();
		return unmarshaller;
	}

	private Marshaller getMarshaller() throws JAXBException {
		JAXBContext context = getJaxbContext();
		Marshaller marshaller = context.createMarshaller();
		return marshaller;
	}

	private JAXBContext getJaxbContext() throws JAXBException {
		JAXBContext context = JAXBContext.newInstance("de.elnarion.xwiki.rest.model.jaxb");
		return context;
	}

	protected boolean addAttachmentToSpacePage(Space paramSpace, File paramAttachment,
			boolean deleteAttachmentBeforeAdding) {
		String relativePath = getRelativeSpacePath(paramSpace.getHome());
		if (deleteAttachmentBeforeAdding) {
			HttpDelete delete = new HttpDelete(getXwikiRestUrl() + relativePath + PAGES_PATH + DEFAULT_PAGE_NAME
					+ ATTACHMENTS_PATH + paramAttachment.getName());
			try (final CloseableHttpResponse response = getHttpClient().execute(delete, getHttpClientContext())) {
			} catch (IOException e) {
				getLog().warn("Could not delete Attachment before adding " + e.getMessage());
				getLog().warn(e);
			}
		}
		HttpPut put = new HttpPut(getXwikiRestUrl() + relativePath + PAGES_PATH + DEFAULT_PAGE_NAME + ATTACHMENTS_PATH
				+ paramAttachment.getName());
		put.addHeader("Accept", "application/xml");
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
				String parentPath = relativePath.substring(0, relativePath.lastIndexOf(SPACES_PATH + spaceName));
				createOrUpdateSpaceWebHomePage(parentPath, spaceName, paramContent);
			} else {
				throw new MojoExecutionException("Pagecontent can not be set ");
			}
		}
		return space;
	}

}
