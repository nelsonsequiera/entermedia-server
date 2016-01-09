package org.entermediadb.asset.convert.inputloaders;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.convert.ConvertInstructions;
import org.entermediadb.asset.convert.ConvertResult;
import org.openedit.page.Page;
import org.openedit.util.ExecResult;

public class ExiftoolthumbCreator extends BaseImageCreator
{
	public boolean canReadIn(MediaArchive inArchive, String inInputType)
	{
		return inInputType != null && inInputType.endsWith("indd");
	}

	public ConvertResult convert(MediaArchive inArchive, Asset inAsset, Page inOut, ConvertInstructions inStructions)
	{
		ConvertResult result = new ConvertResult();
		result.setOk(false);
		
		Page input = inArchive.findOriginalMediaByType("image",inAsset);
		if( input != null)
		{
			new File( inOut.getContentItem().getAbsolutePath() ).getParentFile().mkdirs();
			List base = new ArrayList();
			//command.add("-b");
			//command.add("-ThumbnailImage");
			base.add(input.getContentItem().getAbsolutePath());
			//command.add("-o");
			base.add(inOut.getContentItem().getAbsolutePath());

			List command = new ArrayList(base);			
			command.add("PageImage");
			long timeout = getConversionTimeout(inArchive, inAsset);
			ExecResult done = getExec().runExec("exiftoolthumb",command,timeout);
			if(inOut.length() == 0)
			{
				command = new ArrayList(base);
				command.add("ThumbnailImage");
				done = getExec().runExec("exiftoolthumb",command,timeout);
			}	
			result.setOk(done.isRunOk());
			
		}
		if(inOut.length() == 0){
			inArchive.getPageManager().removePage(inOut);
			result.setOk(false);
			result.setError("no embeded thumbnail found in file");
		}
		return result;
	}

	
}
