package de.elnarion.maven.plugin.xwiki.rest;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class XWikiRESTPageCreationMojoTest extends AbstractMojoTestCase {

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
		System.out.println("TEST MOJO");
		File testPom = new File(getBasedir(), "src/test/resources/unit/xwiki-rest/create-page-test/pom.xml");
		assertNotNull(testPom);
		assertTrue(testPom.exists());

		XWikiRESTPageCreationMojo mojo = (XWikiRESTPageCreationMojo) lookupMojo("createPage", testPom);
		assertNotNull(mojo);

	}
}
