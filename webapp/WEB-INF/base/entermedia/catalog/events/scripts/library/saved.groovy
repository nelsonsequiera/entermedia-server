package library;
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.entermedia.MediaArchive

import com.openedit.hittracker.HitTracker
import com.openedit.hittracker.SearchQuery;
import com.openedit.page.Page
import com.openedit.page.manage.*
import com.openedit.util.Exec
import com.openedit.util.ExecResult

public void init() {
	String id = context.getRequestParameter("id");

	Data library = context.getPageValue("data");
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	if(library == null){
		if( id == null) {
			id = context.getRequestParameter("id.value");
		}
		if( id == null) {
			return;
		}
		library = mediaArchive.getSearcher("library").searchById(id);
	}


	if( library != null ) 
	{
		Searcher libraryusers = mediaArchive.getSearcher("libraryusers");
		String username = context.getUserName();
		if(username != null)
		{
			SearchQuery query = libraryusers.createSearchQuery().append("libraryid", library.id).append("userid", username);
			Data permission = libraryusers.searchByQuery(query);
			if(permission == null)
			{
				Data newentry = libraryusers.createNewData();
				newentry.setId(libraryusers.nextId());
				newentry.setProperty("userid", username);
				newentry.setProperty("libraryid", library.getId());
				//newentry.setProperty("libraryrole", "owner");//not used yet.
				newentry.setSourcePath(library.getSourcePath());
				libraryusers.saveData(newentry, context.getUser());

			}
		}

		String gitprojectname = library.getId();
		//Create Git Repo and check it out
		String gitlocal = mediaArchive.getCatalogSettingValue("project_git_local_root");
		if( gitlocal != null )
		{
			gitlocal = gitlocal + "/" + gitprojectname + ".git";

			File repo = new File( gitlocal );
			if( !repo.exists() )
			{
				Exec exec = (Exec)mediaArchive.getModuleManager().getBean("exec");
				List com = new ArrayList();
				com.add(gitlocal);

				String division = library.get("division");
				
				Page page = pageManager.getPage("/WEB-INF/data/" + mediaArchive.getCatalogId() + "/originals/projects/" + division + "/");					
				String checkoutpath  = page.getContentItem().getAbsolutePath();
				com.add(checkoutpath);
				ExecResult result = exec.runExec("gitaddrepository", com);
				if( !result.isRunOk() )
				{
					context.putPageValue("savemessageerror","Could not create git path");
				}
			}
		}
	}
}

init();

