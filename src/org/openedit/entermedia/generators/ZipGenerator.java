/*
 * Created on Apr 22, 2006
 */
package org.openedit.entermedia.generators;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.generators.BaseGenerator;
import com.openedit.generators.Output;
import com.openedit.page.Page;
import com.openedit.page.manage.PageManager;
import com.openedit.util.PageZipUtil;
import com.openedit.util.PathUtilities;

public class ZipGenerator extends BaseGenerator
{
	protected File fieldRoot;
	protected PageManager pageManager;
	private static final Log log = LogFactory.getLog(ZipGenerator.class);
	public void generate(WebPageRequest inReq, Page inPage, Output inOut) throws OpenEditException
	{
		String path = inReq.getRequestParameter("path");
		if (path.indexOf("..") > -1)
		{
			throw new OpenEditException("Illegal path name");
		}
		//TODO: Add more security checks
		if( inReq.getUser() == null)
		{
			throw new OpenEditException("Illegal user");			
		}
		path = PathUtilities.resolveRelativePath( path, "/");

//		File root = new File( getRoot(), path);
		try
		{
			log.info("Zip up:" + path);
			PageZipUtil pageZipUtil = new PageZipUtil(getPageManager());
			pageZipUtil.setRoot(getRoot());
			pageZipUtil.zipFile(path, inOut.getStream());
		}
		catch ( Exception ex)
		{
			log.error(ex);
		}
	}

	protected File getRoot()
	{
		return fieldRoot;
	}

	public void setRoot(File inRoot)
	{
		fieldRoot = inRoot;
	}
	public boolean canGenerate(WebPageRequest inReq)
	{
		return true;
	}

	public PageManager getPageManager()
	{
		return pageManager;
	}

	public void setPageManager(PageManager inPageManager)
	{
		pageManager = inPageManager;
	}

}
