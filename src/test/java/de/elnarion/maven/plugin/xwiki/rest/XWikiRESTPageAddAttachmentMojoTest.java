package de.elnarion.maven.plugin.xwiki.rest;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class XWikiRESTPageAddAttachmentMojoTest extends AbstractMojoTestCase {

	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	@Before
	protected void setUp() throws Exception {
		// required for mojo lookups to work
		super.setUp();
	}

	/** {@inheritDoc} */
	@After
	protected void tearDown() throws Exception {
		// required
		super.tearDown();
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testMojoConvert() throws Exception {
		File testPom = new File(getBasedir(), "src/test/resources/unit/xwiki-rest/page-add-attachment-test/pom.xml");
		assertNotNull(testPom);
		assertTrue(testPom.exists());
		XWikiRESTPageAddAttachmentMojo mojo = (XWikiRESTPageAddAttachmentMojo) lookupMojo("addAttachment", testPom);
		assertNotNull(mojo);

	}
}
