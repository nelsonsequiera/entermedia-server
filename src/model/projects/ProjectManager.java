package model.projects;

import java.util.Collection;

import org.openedit.Data;
import org.openedit.entermedia.MediaArchive;
import org.openedit.profile.UserProfile;

import com.openedit.WebPageRequest;
import com.openedit.hittracker.HitTracker;
import com.openedit.users.User;

public interface ProjectManager
{
	public Data getCurrentLibrary( UserProfile inProfile);

	public abstract String getCatalogId();

	public abstract void setCatalogId(String inCatId);

	public Collection<UserCollection> loadCollections(WebPageRequest inReq);
	
	public void addAssetToLibrary(WebPageRequest inReq, MediaArchive archive, String libraryid, String assetid);

	public void addAssetToLibrary(WebPageRequest inReq, MediaArchive archive, String libraryid, HitTracker inAssets );

	public void addAssetToCollection(WebPageRequest inReq, MediaArchive archive, String libraryid, HitTracker inAssets );
	
	public void addAssetToCollection(WebPageRequest inReq, MediaArchive archive, String libraryid, String assetid);

	public void removeAssetFromLibrary(WebPageRequest inReq, MediaArchive archive, String libraryid, HitTracker inAssets );

	public void removeAssetFromCollection(WebPageRequest inReq, MediaArchive archive, String collectionid, HitTracker inAssets );

	public HitTracker loadAssetsInCollection(WebPageRequest inReq, MediaArchive archive, String inCollectionId);
	
	public boolean addUserToLibrary(MediaArchive archive, Data inLibrary, User inUser);
	
	public HitTracker loadAssetsInLibrary(Data inLibrary,  MediaArchive archive, WebPageRequest inReq);
	
	//public void savedCollection(MediaArchive archive, Data inLibrary, User inUser);
}