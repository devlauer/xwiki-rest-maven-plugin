package de.elnarion.maven.plugin.xwiki.rest;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import de.elnarion.xwiki.rest.model.jaxb.Space;

@Mojo(name = "addAttachment", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class XWikiRESTPageAddAttachmentMojo extends XWikiRESTAbstractMojo {

	/** The target file name. */
	@Parameter(property = PREFIX + "attachment", required = true)
	private File attachment;

	@Parameter(property = PREFIX + "createSpacePageIfNotExists", required = false, defaultValue = "false")
	private boolean createSpacePageIfNotExists = false;

	@Parameter(property = PREFIX + "pageContent", required = false, defaultValue = "")
	private String pageContent = "";
	
	@Parameter(property = PREFIX + "deleteAttachementBeforeAdding", required = false, defaultValue = "false")
	private boolean deleteAttachmentBeforeAdding = false;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Space space = null;
		if (getPageContent() != null && !getPageContent().isEmpty()) {
			space = createPageWithContent(getPageContent(), isCreateSpacePageIfNotExists());
		} else {
			space = getSpace(isCreateSpacePageIfNotExists());
		}
		if (space != null) {
			boolean ok = addAttachmentToSpacePage(space, attachment,deleteAttachmentBeforeAdding);
			if (!ok) {
				throw new MojoExecutionException("Attachment upload failed");
			}
		} else {
			throw new MojoExecutionException("Space for adding attachment does not exist!");
		}
		closeHTTPClient();
	}

	public File getAttachment() {
		return attachment;
	}

	public void setAttachment(File attachment) {
		this.attachment = attachment;
	}

	public String getPageContent() {
		return pageContent;
	}

	public void setPageContent(String pageContent) {
		this.pageContent = pageContent;
	}

	public boolean isCreateSpacePageIfNotExists() {
		return createSpacePageIfNotExists;
	}

	public void setCreateSpacePageIfNotExists(boolean createSpacePageIfNotExists) {
		this.createSpacePageIfNotExists = createSpacePageIfNotExists;
	}

}
