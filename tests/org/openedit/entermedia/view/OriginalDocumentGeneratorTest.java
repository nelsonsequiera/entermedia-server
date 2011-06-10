package org.openedit.entermedia.view;

import java.io.ByteArrayOutputStream;

import org.openedit.entermedia.Asset;
import org.openedit.entermedia.BaseEnterMediaTest;
import org.openedit.entermedia.EnterMedia;
import org.openedit.entermedia.MediaArchive;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.generators.Output;

public class OriginalDocumentGeneratorTest extends BaseEnterMediaTest
{
	public OriginalDocumentGeneratorTest( String inName )
	{
		super( inName );
	}
	
	public void testGetPrimaryFile() throws OpenEditException
	{
		String path = "entermedia/catalogs/testcatalog/downloads/originals/users/admin/101/asf_to_mpeg-1.mpg";
		EnterMedia em = getEnterMedia();
		MediaArchive archive = em.getMediaArchive("entermedia/catalogs/testcatalog");
		
		Asset asset = archive.getAssetBySourcePath("users/admin/101");
		assertNotNull(asset);
		
		WebPageRequest request = getFixture().createPageRequest( path );
		getFixture().getModuleManager().executePathActions( request.getPage(), request );
		
		Output out = new Output();
		ByteArrayOutputStream baos = (ByteArrayOutputStream) request.getOutputStream();
		out.setStream(	baos );	
		request.getPage().generate( request , out );
		byte[] bytes = baos.toByteArray();
		
		
		assertEquals( "Was not the correct size", 573444, bytes.length );
	}

	public void testGetSourcePath() throws OpenEditException
	{
		String path = "entermedia/catalogs/testcatalog/downloads/originals/users/admin/CHAPTER_1.pdf/CHAPTER_1.pdf";
		EnterMedia em = getEnterMedia();
		MediaArchive archive = em.getMediaArchive("entermedia/catalogs/testcatalog");
		
		Asset asset = archive.getAssetBySourcePath("users/admin/CHAPTER_1.pdf");
		assertNotNull(asset);
		
		WebPageRequest request = getFixture().createPageRequest( path );
		getFixture().getModuleManager().executePathActions( request.getPage(), request );
		
		Output out = new Output();
		ByteArrayOutputStream baos = (ByteArrayOutputStream) request.getOutputStream();
		out.setStream(	baos );	
		request.getPage().generate( request , out );
		byte[] bytes = baos.toByteArray();
		
		assertEquals( "Was not the correct size", 42718, bytes.length );
	}

	
}
