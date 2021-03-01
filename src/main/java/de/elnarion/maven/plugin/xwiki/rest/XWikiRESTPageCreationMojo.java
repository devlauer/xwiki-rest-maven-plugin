package de.elnarion.maven.plugin.xwiki.rest;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "createPage", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class XWikiRESTPageCreationMojo extends XWikiRESTAbstractMojo {

	/** The target file name. */
	@Parameter(property = PREFIX + "content", required = true)
	private String content;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		createPageWithContent(content,true);
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

}
