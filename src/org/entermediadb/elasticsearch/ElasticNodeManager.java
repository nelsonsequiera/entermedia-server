package org.entermediadb.elasticsearch;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsAction;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequestBuilder;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheRequest;
import org.elasticsearch.action.admin.indices.close.CloseIndexAction;
import org.elasticsearch.action.admin.indices.close.CloseIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.open.OpenIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkProcessor.Listener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.transport.RemoteTransportException;
import org.entermediadb.asset.cluster.BaseNodeManager;
import org.openedit.OpenEditException;
import org.openedit.Shutdownable;
import org.openedit.data.PropertyDetailsArchive;
import org.openedit.data.Searcher;
import org.openedit.locks.Lock;
import org.openedit.locks.LockManager;
import org.openedit.page.Page;
import org.openedit.util.FileUtils;
import org.openedit.util.Replacer;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;

//ES5 class MyNode extends Node {
//    public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
//        super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins);
//    }
//}

public class ElasticNodeManager extends BaseNodeManager implements Shutdownable
{
	protected Log log = LogFactory.getLog(ElasticNodeManager.class);

	protected Client fieldClient;
	protected boolean fieldShutdown = false;
	protected List fieldMappingErrors;
	protected Node fieldNode;
	protected Map fieldIndexSettings;
	
	protected void loadSettings()
	{
		// TODO Auto-generated method stub
		//TODO: Move node.xml to system
		//TODO: add locking file for this node and remove it when done

		Page config = getPageManager().getPage("/WEB-INF/node.xml"); //Legacy DO Not use REMOVE sometime
		if (!config.exists())
		{
			//throw new OpenEditException("Missing " + config.getPath());
			config = getPageManager().getPage("/system/configuration/node.xml");
		}

		if( !config.exists())
		{
			throw new OpenEditException("WEB-INF/node.xml is not defined");
		}
		Element root = getXmlUtil().getXml(config.getInputStream(),"UTF-8");
		
		fieldLocalNode = new org.openedit.node.Node();
		String nodeid = getWebServer().getNodeId();
		if( nodeid == null)
		{
			nodeid = root.attributeValue("id");
		}
		if( nodeid == null)
		{
			for (Iterator iterator = root.elementIterator(); iterator.hasNext();)
			{
				Element ele = (Element)iterator.next();
				String key = ele.attributeValue("id");
				if( key.equals("node.name"))
				{
					nodeid = ele.getTextTrim();
				}
			}
		}
		getLocalNode().setId(nodeid);
		String abs = config.getContentItem().getAbsolutePath();
		File parent = new File(abs);
		Map params = new HashMap();

		String webroot = parent.getParentFile().getParentFile().getAbsolutePath();
		params.put("webroot", webroot);
		params.put("nodeid", getLocalNodeId());
		
		getLocalNode().setValue("path.plugins", webroot + "/WEB-INF/base/entermedia/elasticplugins");

		Replacer replace = new Replacer();

		Element basenode = getXmlUtil().getXml(getPageManager().getPage("/system/configuration/basenode.xml").getInputStream(),"UTF-8");
		for (Iterator iterator = basenode.elementIterator(); iterator.hasNext();)
		{
			Element ele = (Element)iterator.next();
			String key = ele.attributeValue("id");
			String val = ele.getTextTrim();
			val = replace.replace(val, params);
			getLocalNode().setValue(key, val);
		}

		
		for (Iterator iterator = root.elementIterator(); iterator.hasNext();)
		{
			Element ele = (Element)iterator.next();
			String key = ele.attributeValue("id");
			String val = ele.getTextTrim();
			//if( val.startsWith("."))
			//{
				val = replace.replace(val, params);
			//}
			getLocalNode().setValue(key, val);
		}
		getLocalNode().setValue("node.name", nodeid);

	}

	protected boolean reindexing = false;
	
	public List getMappingErrors()
	{
		if (fieldMappingErrors == null)
		{
			fieldMappingErrors = new ArrayList<>();
		}
		return fieldMappingErrors;
	}

	public void setMappingErrors(List inMappingErrors)
	{
		fieldMappingErrors = inMappingErrors;
	}

	public Client getClient()
	{
		if (fieldShutdown == false && fieldClient == null)
		{
			synchronized (this)
			{
				if (fieldClient != null)
				{
					return fieldClient;
				}
				NodeBuilder nb = NodeBuilder.nodeBuilder();
				//ES5: Settings.Builder preparedsettings = Settings.builder();

				for (Iterator iterator = getLocalNode().getProperties().keySet().iterator(); iterator.hasNext();)
				{
					String key = (String) iterator.next();
					if(!key.startsWith("index.") && !key.startsWith("entermedia.") && key.contains(".") ) //Legacy
					{
						String val = getLocalNode().getSetting(key);
						//ES5: preparedsettings.put(key, val);
						nb.settings().put(key, val);
					}	
				}
				fieldNode = nb.node();
				fieldClient = fieldNode.client(); //when this line executes, I get the error in the other node 
				
				//nb.settings().put("index.mapper.dynamic",false);
				
				//			     <property id="path.plugins">${webroot}/WEB-INF/base/entermedia/elasticplugins</property>

				//extras
				//nb.settings().put("index.store.type", "mmapfs");
				//nb.settings().put("index.store.fs.mmapfs.enabled", "true");
				//nb.settings().put("index.merge.policy.merge_factor", "20");
				// nb.settings().put("discovery.zen.ping.unicast.hosts", "localhost:9300");
				// nb.settings().put("discovery.zen.ping.unicast.hosts", elasticSearchHostsList);
				//fieldNode = nb.node();
				//fieldClient = fieldNode.client(); //when this line executes, I get the error in the other node 
		
				
			}
		}
		return fieldClient;
	}

	//called from the lock manager
	public void shutdown()
	{

		try
		{
			synchronized (this)
			{

				if (!fieldShutdown)
				{
					if (fieldClient != null)
					{
						try
						{
							//TODO: Should we call FlushRequest req = Requests.flushRequest(toId(getCatalogId()));  ? To The disk drive
							fieldClient.close();
						}
						finally
						{
							if (fieldNode != null)
							{
								fieldNode.close();
							}
							fieldNode.close();
						}
					}
					if (fieldNode != null)
					{
						fieldNode.close();
					}
				}
				fieldShutdown = true;
				System.out.println("Elastic shutdown complete");
			}
		}
		catch (Exception e)
		{
			System.out.println("Elastic shutdown failed");
			e.printStackTrace();
			throw new OpenEditException(e);
		}
	}

	public String toId(String inId)
	{
		String id = inId.replace('/', '_');
		return id;
	}

	protected LockManager getLockManager(String inCatalogId)
	{
		return getSearcherManager().getLockManager(inCatalogId);
	}

	public String createSnapShot(String inCatalogId, boolean wholecluster)
	{
		Lock lock = null;
		try
		{
			lock = getLockManager(inCatalogId).lock("snapshot", "elasticNodeManager");
			return createSnapShot(inCatalogId, lock, wholecluster);
		}
		finally
		{
			getLockManager(inCatalogId).release(lock);
		}
	}

	public String createSnapShot(String inCatalogId, Lock inLock, boolean wholecluster)
	{
		String indexid = toId(inCatalogId);
		String path = getLocalNode().getSetting("path.repo") + "/" + indexid; //Store it someplace unique so we can be isolated?

		//log.info("Deleted nodeid=" + id + " records database " + getSearchType() );

		Settings settings = Settings.builder().put("location", path).build();
		PutRepositoryRequestBuilder putRepo = new PutRepositoryRequestBuilder(getClient().admin().cluster(), PutRepositoryAction.INSTANCE);
		putRepo.setName(indexid).setType("fs").setSettings(settings) //With Unique location saved for each catalog
				.execute().actionGet();

		//	    PutRepositoryRequestBuilder putRepo = 
		//	    		new PutRepositoryRequestBuilder(getClient().admin().cluster());
		//	    putRepo.setName(indexid)
		//	            .setType("fs")
		//	            .setSettings(settings) //With Unique location saved for each catalog
		//	            .execute().actionGet();

		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		//  CreateSnapshotRequestBuilder(elasticSearchClient, CreateSnapshotAction.INSTANCE)

		CreateSnapshotRequestBuilder builder = new CreateSnapshotRequestBuilder(getClient(), CreateSnapshotAction.INSTANCE);
		String snapshotid = format.format(new Date());
		if (!wholecluster)
		{
			builder.setRepository(indexid).setIndices(indexid).setWaitForCompletion(true).setSnapshot(snapshotid);
		}
		else
		{
			builder.setRepository(indexid).setWaitForCompletion(true).setSnapshot(snapshotid + "-full");
		}
		builder.execute().actionGet();

		return snapshotid;
	}

	public String createDailySnapShot(String inCatalogId)
	{
		return createDailySnapShot(inCatalogId, false);
	}

	public String createDailySnapShot(String inCatalogId, boolean wholedatabase)
	{
		Lock lock = null;

		try
		{
			lock = getLockManager(inCatalogId).lock("snapshot", "elasticNodeManager");

			List list = listSnapShots(inCatalogId);
			if (list.size() > 0)
			{
				SnapshotInfo recent = (SnapshotInfo) list.iterator().next();
				Date date = new Date(recent.startTime());
				Calendar yesterday = new GregorianCalendar();
				yesterday.add(Calendar.DAY_OF_YEAR, -1);
				if (date.after(yesterday.getTime()))
				{
					return String.valueOf(recent.startTime());
				}
			}
			return createSnapShot(inCatalogId, lock, wholedatabase);
		}
		catch (Throwable ex)
		{
			throw new OpenEditException(ex);
		}
		finally
		{
			getLockManager(inCatalogId).release(lock);
		}
	}

	public List listSnapShots(String inCatalogId)
	{
		String indexid = toId(inCatalogId);

		String path = getLocalNode().getSetting("path.repo") + "/" + indexid;

		if (!new File(path).exists())
		{
			return Collections.emptyList();
		}
		Settings settings = Settings.builder().put("location", path).build();

		PutRepositoryRequestBuilder putRepo = new PutRepositoryRequestBuilder(getClient().admin().cluster(), PutRepositoryAction.INSTANCE);
		putRepo.setName(indexid).setType("fs").setSettings(settings) //With Unique location saved for each catalog
				.execute().actionGet();

		GetSnapshotsRequestBuilder builder = new GetSnapshotsRequestBuilder(getClient(), GetSnapshotsAction.INSTANCE);
		builder.setRepository(indexid);

		GetSnapshotsResponse getSnapshotsResponse = builder.execute().actionGet();
		List results = new ArrayList(getSnapshotsResponse.getSnapshots());

		Collections.sort(results, new Comparator<SnapshotInfo>()
		{
			@Override
			public int compare(SnapshotInfo inO1, SnapshotInfo inO2)
			{
				return (int) (inO1.startTime()-inO2.startTime());
			}
		});
		Collections.reverse(results);
		return results;
	}

	public void restoreSnapShot(String inCatalogId, String inSnapShotId)
	{
		String indexid = toId(inCatalogId);
		listSnapShots(indexid);

		// String reponame = indexid + "_repo";

		// Obtain the snapshot and check the indices that are in the snapshot
		AdminClient admin = getClient().admin();

		//TODO: Close index!!

		try
		{
			ClusterHealthResponse health = admin.cluster().prepareHealth(indexid).setWaitForYellowStatus().execute().actionGet();
		}
		catch (Exception ex)
		{
			log.error(ex);
			throw new OpenEditException(ex);
		}

		String currentindex = getIndexNameFromAliasName(indexid); //This is the current index that the alias 
		clearAlias(indexid);
		boolean undo = false;
		try
		{
			//Close it first
			CloseIndexRequestBuilder closeIndexRequestBuilder = new CloseIndexRequestBuilder(getClient(), CloseIndexAction.INSTANCE);
			closeIndexRequestBuilder.setIndices(currentindex);
			closeIndexRequestBuilder.execute().actionGet();
			// Now execute the actual restore action
			//Sleep a little?
			
			RestoreSnapshotRequestBuilder restoreBuilder = new RestoreSnapshotRequestBuilder(getClient(), RestoreSnapshotAction.INSTANCE);
			restoreBuilder.setRepository(indexid).setSnapshot(inSnapShotId).setWaitForCompletion(true);
			RestoreSnapshotResponse response = restoreBuilder.execute().actionGet();
			//Cant read index information on a closed index
			List <String> restored = response.getRestoreInfo().indices();
			if(restored.isEmpty())
			{
				loadIndex(indexid, currentindex, false);
				throw new OpenEditException("Cannot Restore Snapshot - restored" + restored +" indeces");
			}
			String loadedindexid = getIndexNameFromAliasName(indexid);
			if (!loadedindexid.equals(currentindex))
			{
				DeleteIndexResponse delete = getClient().admin().indices().delete(new DeleteIndexRequest(currentindex)).actionGet();
			}
			ClusterHealthResponse health = admin.cluster().prepareHealth(indexid).setWaitForYellowStatus().execute().actionGet();
		}
		catch (Exception ex)
		{
			undo = true;
			log.error("Could not restore" , ex);
		}
		try
		{
			if( undo)
			{
				admin.indices().open(new OpenIndexRequest(currentindex));				
			}
			else
			{
				admin.indices().open(new OpenIndexRequest(indexid));				
			}
			ClusterHealthResponse health = admin.cluster().prepareHealth(indexid).setWaitForYellowStatus().execute().actionGet();
		}
		catch (Exception ex)
		{
			log.error("Could to finalize " + undo,ex);
			throw new OpenEditException(ex);
		}
}

	//	public void exportKnapsack(String inCatalogId)
	//	{
	//		Lock lock = null;
	//
	//		try
	//		{
	//			String indexid = toId(inCatalogId);
	//
	//			lock = getLockManager(inCatalogId).lock("snapshot", "elasticNodeManager");
	//			Client client = getClient();
	//			Date date = new Date();
	//			Page target = getPageManager().getPage("/WEB-INF/data/" + inCatalogId + "/snapshots/knapsack-bulk-" + date.getTime() + ".bulk.gz");
	//			Page folder = getPageManager().getPage(target.getParentPath());
	//			File file = new File(folder.getContentItem().getAbsolutePath());
	//			file.mkdirs();
	//			Path exportPath = Paths.get(URI.create("file:" + target.getContentItem().getAbsolutePath()));
	//			KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client.admin().indices()).setArchivePath(exportPath).setOverwriteAllowed(true).setIndex(indexid);
	//			KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
	//
	//			KnapsackStateRequestBuilder knapsackStateRequestBuilder = new KnapsackStateRequestBuilder(client.admin().indices());
	//			KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
	//			knapsackStateResponse.isExportActive(exportPath);
	//			Thread.sleep(1000L);
	//			// delete index
	//			//		        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
	//			//		        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1").admin().indices())
	//			//		                .setPath(exportPath);
	//			//		        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
	//			//		        if (!knapsackImportResponse.isRunning()) {
	//			//		            logger.error(knapsackImportResponse.getReason());
	//			//		        }
	//			//		        assertTrue(knapsackImportResponse.isRunning());
	//			//		        Thread.sleep(1000L);
	//			//		        // count
	//			//		        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
	//			//		        assertEquals(1L, count);
	//		}
	//		catch (Throwable ex)
	//		{
	//			throw new OpenEditException(ex);
	//		}
	//		finally
	//		{
	//			getLockManager(inCatalogId).release(lock);
	//		}
	//	}
	//
	//	public HitTracker listKnapsacks(String inCatalogId)
	//	{
	//		List snaps = getPageManager().getChildrenPathsSorted("/WEB-INF/data/" + inCatalogId + "/snapshots/");
	//		ListHitTracker tracker = new ListHitTracker();
	//		for (Iterator iterator = snaps.iterator(); iterator.hasNext();)
	//		{
	//			String path = (String) iterator.next();
	//			Page page = getPageManager().getPage(path);
	//			tracker.add(page);
	//		}
	//		return tracker;
	//	}
	//
	//	public void importKnapsack(String inCatalogId, String inFile)
	//	{
	//		Lock lock = null;
	//
	//		try
	//		{
	//			lock = getLockManager(inCatalogId).lock("snapshot", "elasticNodeManager");
	//			String indexid = toId(inCatalogId);
	//
	//			Page target = getPageManager().getPage("/WEB-INF/data/" + inCatalogId + "/snapshots/" + inFile);
	//
	//			Client client = getClient();
	//			File exportFile = new File(target.getContentItem().getAbsolutePath());
	//			Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
	//			KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client.admin().indices()).setArchivePath(exportPath).setIndex(indexid);
	//			KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
	//
	//		}
	//		catch (Throwable ex)
	//		{
	//			throw new OpenEditException(ex);
	//		}
	//		finally
	//		{
	//			getLockManager(inCatalogId).release(lock);
	//		}
	//	}

	public void addMappingError(String inSearchType, String inMessage)
	{
		MappingError error = new MappingError();
		error.setError(inMessage);
		error.setSearchType(inSearchType);
		if (inMessage.contains("Mapper for ["))
		{
			String guessdetail = inMessage.substring("Mapper for [".length(), inMessage.length());
			guessdetail = guessdetail.substring(0, guessdetail.indexOf("]"));
			error.setDetail(guessdetail);
		}
		
		
		if (inMessage.contains("cannot be changed"))
		{
			String guessdetail = inMessage.substring("mapper [".length(), inMessage.length());
			guessdetail = guessdetail.substring(0, guessdetail.indexOf("]"));
			error.setDetail(guessdetail);
		}
		
		

		getMappingErrors().add(error);

	}

	public boolean hasMappingErrors()
	{
		return !getMappingErrors().isEmpty();
	}

	public boolean containsCatalog(String inCatalogId)
	{
		if (getConnectedCatalogIds().containsKey(inCatalogId))
		{
			return true;
		}
		String index = toId(inCatalogId);
		IndicesExistsRequest existsreq = Requests.indicesExistsRequest(index);
		IndicesExistsResponse res = getClient().admin().indices().exists(existsreq).actionGet();
		return res.isExists();
	}

	public boolean connectCatalog(String inCatalogId)
	{
		if (!getConnectedCatalogIds().containsKey(inCatalogId))
		{
			synchronized (this)
			{
				if (!getConnectedCatalogIds().containsKey(inCatalogId))
				{
					String alias = toId(inCatalogId);
					
					AdminClient admin = getClient().admin();
					ClusterHealthResponse health = admin.cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

					if (health.isTimedOut())
					{
						throw new OpenEditException("Could not get yellow status for " + alias);
					}

					
					String index = getIndexNameFromAliasName(alias);//see if we already have an index
		
					//see if an actual index exists
		
					if (index == null)
					{
						index = alias + "-0";
					}
					IndicesExistsRequest existsreq = Requests.indicesExistsRequest(index); //see if 
					IndicesExistsResponse res = admin.indices().exists(existsreq).actionGet();
					//			if (res.isExists() ){
					//				index = alias;
					//			}
		
		
					boolean createdIndex = prepareIndex(index);
					getConnectedCatalogIds().put(inCatalogId, index);
					if (createdIndex)
					{
						if (!res.isExists())
						{
							admin.indices().prepareAliases().addAlias(index, alias).execute().actionGet();//This sets up an alias that the app uses so we can flip later.
						}
		
					}
					//			PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(inCatalogId);
					//			List sorted = archive.listSearchTypes();
					//			for (Iterator iterator = sorted.iterator(); iterator.hasNext();)
					//			{
					//				String type = (String) iterator.next();
					//				Searcher searcher = getSearcherManager().getSearcher(inCatalogId, type);
					//				searcher.initialize();	
					//			}
					return true;
				}	
			}	
		}
		return false;//Created a ne
	}

	public boolean prepareIndex(String index)
	{

		AdminClient admin = getClient().admin();

		IndicesExistsRequest existsreq = Requests.indicesExistsRequest(index);
		IndicesExistsResponse res = admin.indices().exists(existsreq).actionGet();
		boolean createdIndex = false;
		if (!res.isExists())
		{
			InputStream in = null;
			try
			{
				Page yaml = getPageManager().getPage("/system/configuration/elasticindex.yaml");
				in = yaml.getInputStream();
				Builder settingsBuilder = Settings.builder().loadFromStream(yaml.getName(), in);
				CreateIndexResponse newindexres = admin.indices().prepareCreate(index).setSettings(settingsBuilder).execute().actionGet();
 
				
				/*
				 * 	XContentBuilder settingsBuilder = XContentFactory.jsonBuilder()
					.startObject()
						.startObject("analysis")
							.startObject("analyzer")
								.startObject("lowersnowball").field("tokenizer", "standard").startArray("filter").value("standard").value("lowercase").value("stemfilter").endArray()
								.endObject()
							.endObject()
							.startObject("analyzer")
								.startObject("tags").field("type", "custom").field("tokenizer", "keyword").startArray("filter").value("lowercase").endArray()					
								.endObject()
							.endObject()
							.startObject("filter")
								.startObject("stemfilter").field("type","snowball").field("language","English")
								.endObject()
							.endObject()
						.endObject();
				 */
				
				if (newindexres.isAcknowledged())
				{
					log.info("index created " + index);
				}
				createdIndex = true;

			}
			catch (RemoteTransportException exists)
			{
				// silent error
				log.debug("Index already exists " + index);
			}
			catch (Exception e)
			{
				if(e instanceof RuntimeException )
				{
					throw (RuntimeException)e;
				}
				throw new OpenEditException(e);
			}
			finally
			{
				FileUtils.safeClose(in);
			}
			return createdIndex;
		}

		RefreshRequest req = Requests.refreshRequest(index);
		RefreshResponse rres = admin.indices().refresh(req).actionGet();
		if (rres.getFailedShards() > 0)
		{
			log.error("Could not refresh shards");
		}

		return createdIndex;
	}

	

	public String getIndexNameFromAliasName(final String aliasName)
	{
		AliasOrIndex indexToAliasesMap = getClient().admin().cluster().state(Requests.clusterStateRequest()).actionGet().getState().getMetaData().getAliasAndIndexLookup().get(aliasName);

		if (indexToAliasesMap == null)
		{
			return null;
		}

		if (indexToAliasesMap.isAlias() && indexToAliasesMap.getIndices().size() > 0)
		{
			//ES5 return indexToAliasesMap.getIndices().iterator().next().getIndex().getName();
			return indexToAliasesMap.getIndices().iterator().next().getIndex();
		}

		return null;
	}

	protected void clearAlias(final String aliasName)
	{

		AliasOrIndex indexToAliasesMap = getClient().admin().cluster().state(Requests.clusterStateRequest()).actionGet().getState().getMetaData().getAliasAndIndexLookup().get(aliasName);

		if (indexToAliasesMap == null)
		{
			return;
		}

		if (indexToAliasesMap.isAlias() && indexToAliasesMap.getIndices().size() > 0)
		{
			for (Iterator iterator = indexToAliasesMap.getIndices().iterator(); iterator.hasNext();)
			{
				IndexMetaData metadata = (IndexMetaData) iterator.next();
				//ES5 String indexid = metadata.getIndex().getName();
				String indexid = metadata.getIndex();
				getClient().admin().indices().prepareAliases().removeAlias(indexid, aliasName).execute().actionGet();

			}
		}

	}

	public boolean reindexInternal(String inCatalogId)
	{
		try
		{
			createSnapShot(inCatalogId);
		}
		catch( Throwable ex)
		{
			log.error("Could not get snapshot" , ex);
		}
		if( reindexing )
		{
			throw new OpenEditException("Already reindexing");
		}
		reindexing = true;
		
		getPageManager().clearCache();
		getSearcherManager().getCacheManager().clearAll();
		
		String id = toId(inCatalogId);
		List mappedtypes = getMappedTypes(id);

		String newindex = null;
		String searchtype = null;
		try
		{
			PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(inCatalogId);
			archive.clearCache();
			getPageManager().clearCache();
			getSearcherManager().getCacheManager().clearAll();
			
			newindex = prepareTemporaryIndex(inCatalogId, mappedtypes);

			mappedtypes.remove("lock");
			
			for (Iterator iterator = mappedtypes.iterator(); iterator.hasNext();)
			{
				searchtype = (String) iterator.next();
				Searcher searcher = getSearcherManager().getSearcher(inCatalogId, searchtype);
				
				searcher.setAlternativeIndex(newindex);//Should		
				long start = System.currentTimeMillis();
				searcher.reindexInternal();
				long end = System.currentTimeMillis();
				log.info("Reindex of " + searchtype + " took " + (end - start) / 1000L);
				searcher.setAlternativeIndex(null);
			}

			loadIndex(id, newindex, true);
			getSearcherManager().clear();
			
		}
		catch (Throwable e)
		{
			log.error("Could not reindex " + searchtype);
			if (newindex != null)
			{
				DeleteIndexResponse delete = getClient().admin().indices().delete(new DeleteIndexRequest(newindex)).actionGet();
				if (!delete.isAcknowledged())
				{
					log.error("Index wasn't deleted");
				}
			}
			if( e instanceof OpenEditException)
			{
				throw e;
			}
			throw new OpenEditException(e);
		}
		finally
		{
			reindexing = false;
		}
		return true;

	}

	public String prepareTemporaryIndex(String inCatalogId, List mappedtypes)
	{
		Date date = new Date();
		String id = toId(inCatalogId);
		String tempindex = id + date.getTime();
		prepareIndex(tempindex);
		//need to reset/creat the mappings here!
		getMappingErrors().clear();

		for (Iterator iterator = mappedtypes.iterator(); iterator.hasNext();)
		{
			String searchtype = (String) iterator.next();
			Searcher searcher = getSearcherManager().getSearcher(inCatalogId, searchtype);
			searcher.setAlternativeIndex(tempindex);//Should				
			if( !searcher.putMappings() )
			{
				log.error("Could not map " + searchtype);
			}
			searcher.setAlternativeIndex(null);
		}

		if (!getMappingErrors().isEmpty())
		{
			DeleteIndexResponse delete = getClient().admin().indices().delete(new DeleteIndexRequest(tempindex)).actionGet();
			if (!delete.isAcknowledged())
			{
				log.error("Index wasn't deleted");
			}
			throw new OpenEditException("Was unable to create new mappings, inconsistent");
		}
		return tempindex;

	}

	public List getMappedTypes(String inCatalogId)
	{
		GetMappingsRequest req = new GetMappingsRequest().indices(inCatalogId);
		GetMappingsResponse resp = getClient().admin().indices().getMappings(req).actionGet();
		String oldindex = getIndexNameFromAliasName(inCatalogId);

		ArrayList types = new ArrayList();
		  ImmutableOpenMap<String, MappingMetaData> mapping  = resp.mappings().get(oldindex);
		   for (ObjectObjectCursor<String, MappingMetaData> c : mapping) {
		      // System.out.println(c.key+" = "+c.value.source());
		       types.add(c.key);
		   }

		PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(inCatalogId);
		final List withparents = archive.findChildTablesNames();
		
		types.sort(new Comparator()
		{
			@Override
			public int compare(Object inO1, Object inO2)
			{
				if( withparents.contains(inO1))
				{
					return 1;
				}
				if( withparents.contains(inO2))
				{
					return -1;
				}
				return 0;
			}
		});
		types.remove("category");
		types.add(0,"category");
		return types;
		
	}

	public boolean checkAllMappings(String inCatalogId)
	{
		Date date = new Date();
		String id = toId(inCatalogId);

		String tempindex = id + date.getTime();
		prepareIndex(tempindex);
		//need to reset/creat the mappings here!
		getMappingErrors().clear();
		PropertyDetailsArchive archive = getSearcherManager().getPropertyDetailsArchive(inCatalogId);
		List withparents = archive.findChildTablesNames();

		for (Iterator iterator = withparents.iterator(); iterator.hasNext();)
		{
			String searchtype = (String) iterator.next();
			Searcher searcher = getSearcherManager().getSearcher(inCatalogId, searchtype);
			searcher.setAlternativeIndex(tempindex);//Should				
			searcher.putMappings();
			searcher.setAlternativeIndex(null);

		}

		List sorted = archive.listSearchTypes();
		for (Iterator iterator = sorted.iterator(); iterator.hasNext();)
		{

			String searchtype = (String) iterator.next();
			if (!withparents.contains(searchtype))
			{

				Searcher searcher = getSearcherManager().getSearcher(inCatalogId, searchtype);

				searcher.setAlternativeIndex(tempindex);//Should				
				searcher.putMappings();
				searcher.setAlternativeIndex(null);
			}

		}
		DeleteIndexResponse delete = getClient().admin().indices().delete(new DeleteIndexRequest(tempindex)).actionGet();
		if (!delete.isAcknowledged())
		{
			log.error("Index wasn't deleted");
		}
		if (getMappingErrors().size() == 0)
		{
			return true;
		}
		else
		{
			return false;
		}

	}

	public void loadIndex(String id, String inTarget, boolean dropold)
	{
		id = toId(id);

		String oldindex = getIndexNameFromAliasName(id);
		getClient().admin().indices().prepareAliases().removeAlias(oldindex, id).addAlias(inTarget, id).execute().actionGet();
		if (dropold)
		{

			DeleteIndexResponse response = getClient().admin().indices().delete(new DeleteIndexRequest(oldindex)).actionGet();
			log.info("Dropped: " + response.isAcknowledged());
		}

	}

	private Listener createLoggingBulkProcessorListener()
	{
		return new BulkProcessor.Listener()
		{
			@Override
			public void beforeBulk(long executionId, BulkRequest request)
			{
			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, BulkResponse response)
			{
			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, Throwable failure)
			{
			}
		};
	}

	public void removeMappingError(String inSearchType)
	{
		// TODO Auto-generated method stub
		getMappingErrors().remove(inSearchType);
	}

	@Override
	public void deleteCatalog(String inId)
	{
		DeleteIndexResponse delete = getClient().admin().indices().delete(new DeleteIndexRequest(toId(inId))).actionGet();
		if (!delete.isAcknowledged())
		{
			log.error("Index wasn't deleted");
		}

	}

	@Override
	public String createSnapShot(String inCatalogId)
	{
		return createSnapShot(inCatalogId, false);
	}

	public boolean tableExists(String indexid, String searchtype)
	{
		boolean used = getClient().admin().indices().typesExists(new TypesExistsRequest(new String[] { indexid }, searchtype)).actionGet().isExists();
		return used;

	}
	
	public void clear(){
		getClient().admin().indices().clearCache(new ClearIndicesCacheRequest()).actionGet();
	}

	
	
	public BulkProcessor getBulkProcessor(final ArrayList errors) {
		BulkProcessor bulkProcessor = BulkProcessor.builder(getClient(), new BulkProcessor.Listener()
		{
			@Override
			public void beforeBulk(long executionId, BulkRequest request)
			{

			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, BulkResponse response)
			{
				for (int i = 0; i < response.getItems().length; i++)
				{
					// request.getFromContext(key)
					BulkItemResponse res = response.getItems()[i];
					if (res.isFailed())
					{
						log.info(res.getFailureMessage());
						errors.add(res.getFailureMessage());

					}
					// Data toupdate = toversion.get(res.getId());
					
				}
				//	request.refresh(true);
			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, Throwable failure)
			{
				log.info(failure);
				errors.add(failure);
			}
		}).setBulkActions(-1).setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB)).setFlushInterval(TimeValue.timeValueMinutes(4)).setConcurrentRequests(1).setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 10)).build();
		
		
		return bulkProcessor;
	}
	
	
	public NodeStats getNodeStats(){
		
		
		 NodesStatsResponse response = getClient().admin().cluster().prepareNodesStats().setJvm(true).setFs(true).setOs(true).setNodesIds(getLocalNodeId()).setThreadPool(true).get();
		 
		 ClusterHealthResponse healths = getClient().admin().cluster().prepareHealth().get();
		 for (Iterator iterator = response.getNodesMap().keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			NodeStats stats = response.getNodesMap().get(key);
			String id = stats.getNode().getId();
			String name = stats.getNode().getName();
			if(getLocalNodeId().equals(name)){
				stats.getJvm().getMem().getHeapMax();
				stats.getJvm().getMem().getHeapUsed();
				stats.getJvm().getMem().getHeapUsedPercent();
				stats.getOs().getCpuPercent();
				stats.getOs().getLoadAverage();
				stats.getOs().getMem().getFree();
				stats.getFs().getTotal();
				log.info(response);

				return stats;
			}
		}
		 
		// NodeStats stats = response.getNodesMap().get(getLocalNodeId());

		 
//		 stats.getJvm().getMem().getHeapCommitted();
		 return null;
	}
	
}
