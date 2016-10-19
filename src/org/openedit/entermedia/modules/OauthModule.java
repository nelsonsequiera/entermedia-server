package org.openedit.entermedia.modules;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.modules.BaseMediaModule;
import org.entermediadb.elasticsearch.searchers.BaseElasticSearcher;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.WebPageRequest;
import org.openedit.entermedia.util.EmTokenResponse;
import org.openedit.event.WebEvent;
import org.openedit.event.WebEventListener;
import org.openedit.page.PageRequestKeys;
import org.openedit.users.User;
import org.openedit.users.UserSearcher;
import org.openedit.util.StringEncryption;
import org.openedit.util.URLUtilities;

public class OauthModule extends BaseMediaModule
{	
	private static final Log log = LogFactory.getLog(OauthModule.class);

	
	protected StringEncryption fieldCookieEncryption;
	protected WebEventListener fieldWebEventListener;


	public WebEventListener getWebEventListener()
	{
		return fieldWebEventListener;
	}

	public void setWebEventListener(WebEventListener inWebEventListener)
	{
		fieldWebEventListener = inWebEventListener;
	}

	public StringEncryption getCookieEncryption()
	{
		return fieldCookieEncryption;
	}

	public void setCookieEncryption(StringEncryption inCookieEncryption)
	{
		fieldCookieEncryption = inCookieEncryption;
	}

	public void redirectToHost(WebPageRequest inReq)
	{
		//http://yfrankfeng.blogspot.ca/2015/07/working-example-on-oauth2-spring.html
		
		
		
		try
		{

			String provider = inReq.findValue("provider");
			MediaArchive archive = getMediaArchive(inReq);
			String appid = inReq.findValue("applicationid");
			Data authinfo = archive.getData("oauthprovider", provider);
			String siteroot = inReq.findValue("siteroot");
			
			URLUtilities utils = (URLUtilities) inReq
					.getPageValue(PageRequestKeys.URL_UTILITIES);
			if (siteroot == null && utils != null) {

				siteroot = utils.siteRoot();
			}
			
			String redirect = siteroot + "/" + appid + authinfo.get("redirecturi");
			if ("google".equals(provider))
			{

				//.setClientId("1028053038230-v8g3isffne0b6d3vj8ceok61h2bfk9hg.apps.googleusercontent.com")
				//.setRedirectURI("http://localhost:8080/googleauth.html")

				OAuthClientRequest request = OAuthClientRequest.authorizationProvider(OAuthProviderType.GOOGLE).setClientId(authinfo.get("clientid")).setRedirectURI(redirect).setResponseType("code").setScope("https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid").buildQueryMessage();

				String locationUri = request.getLocationUri();
				inReq.redirect(locationUri);
			}

			if ("drupal".equals(provider))
			{
				//"https://devcondrupal.genieve.com/oauth2/authorize"
				//"devemgenieve"
				OAuthClientRequest request = OAuthClientRequest.authorizationLocation(authinfo.get("remoteroot") + "/oauth2/authorize").setClientId(authinfo.get("clientid")).setRedirectURI(redirect).setResponseType("code").setScope("openid email profile").setState("login").buildQueryMessage();

				String locationUri = request.getLocationUri();
				inReq.redirect(locationUri);

			}

		}
		catch (Exception e)
		{
			throw new OpenEditException(e);
		}
	}

	public void login(WebPageRequest inReq) throws Exception
	{

		String provider = inReq.findValue("provider");
		String appid = inReq.findValue("applicationid");
		MediaArchive archive = getMediaArchive(inReq);

		String siteroot = inReq.findValue("siteroot");
		
		URLUtilities utils = (URLUtilities) inReq
				.getPageValue(PageRequestKeys.URL_UTILITIES);
		if (siteroot == null && utils != null) {

			siteroot = utils.siteRoot();
		}
		
		
		if ("google".equals(provider))
		{

			Data authinfo = archive.getData("oauthprovider", provider);

			OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(inReq.getRequest());
			String code = oar.getCode();
			//GOOGLE

			OAuthClientRequest request = OAuthClientRequest.tokenProvider(OAuthProviderType.GOOGLE).setGrantType(GrantType.AUTHORIZATION_CODE).setClientId(authinfo.get("clientid")).setClientSecret(authinfo.get("clientsecret")).setRedirectURI(siteroot + "/" + appid + authinfo.get("redirecturi")).setCode(code).buildBodyMessage();

			try
			{

				OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
				//Facebook is not fully compatible with OAuth 2.0 draft 10, access token response is
				//application/x-www-form-urlencded, not json encoded so we use dedicated response class for that
				//Own response class is an easy way to deal with oauth providers that introduce modifications to
				//OAuth specification
				EmTokenResponse oAuthResponse = oAuthClient.accessToken(request, EmTokenResponse.class);

				// final OAuthAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request, "POST");
				// final OAuthAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request);
				String accessToken = oAuthResponse.getAccessToken();

				OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest("https://www.googleapis.com/oauth2/v1/userinfo").setAccessToken(accessToken).buildQueryMessage();
				OAuthResourceResponse resourceResponse = oAuthClient.resource(bearerClientRequest, "GET", OAuthResourceResponse.class);
				String userinfoJSON = resourceResponse.getBody();
				JSONParser parser = new JSONParser();
				
				JSONObject data =  (JSONObject) parser.parse(userinfoJSON);
				String email = (String)data.get("email");
				String firstname = (String)data.get("given_name");
				String lastname = (String)data.get("family_name");
				boolean autocreate = Boolean.parseBoolean(authinfo.get("autocreateusers"));
				handleLogin(inReq,  email, firstname, lastname,true, autocreate, authinfo);
				
			}
			catch (Exception e)
			{
				throw new OpenEditException(e);
			}
		}

		if ("drupal".equals(provider))
		{
			Data authinfo = archive.getData("oauthprovider", provider);

			OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(inReq.getRequest());
			String code = oar.getCode();

			String redirect = siteroot + "/" + appid + authinfo.get("redirecturi");
			OAuthClientRequest request = OAuthClientRequest.tokenLocation(authinfo.get("remoteroot") + "/oauth2/token").setGrantType(GrantType.AUTHORIZATION_CODE).setClientId(authinfo.get("clientid")).setClientSecret(authinfo.get("clientsecret")).setRedirectURI(redirect).setCode(code).buildBodyMessage();
			OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
			EmTokenResponse oAuthResponse = oAuthClient.accessToken(request, EmTokenResponse.class);
			// final OAuthAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request, "POST");
			// final OAuthAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request);

			String accessToken = oAuthResponse.getAccessToken();

			OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest(authinfo.get("remoteroot") + "/oauth2/UserInfo").setAccessToken(accessToken).buildQueryMessage();

			OAuthResourceResponse resourceResponse = oAuthClient.resource(bearerClientRequest, "GET", OAuthResourceResponse.class);
			String userinfoJSON = resourceResponse.getBody();
			JSONParser parser = new JSONParser();
			
			JSONObject data =  (JSONObject) parser.parse(userinfoJSON);
			handleLogin(inReq,  (String)data.get("email"), (String)data.get("name"), (String)data.get("lastname"),false, true, authinfo);
			
		}
	//	inReq.redirect("/" + appid + "/index.html");
	}

	protected void handleLogin(WebPageRequest inReq,  String email, String firstname, String lastname, boolean matchOnEmail, boolean autocreate, Data authinfo)
	{
		 
		if(authinfo.getValue("alloweddomains") != null){
			
			boolean ok = false;
			String domains = authinfo.get("alloweddomains");
			String [] domainlist = domains.split(",");
			for (int i = 0; i < domainlist.length; i++)
			{
				String domain = domainlist[i];
				if(email.endsWith(domain)){
					ok = true;
				}
			}
			if(!ok)
			{
				String appid = inReq.findValue("applicationid");
				inReq.redirect("/" + appid + "/authentication/nopermissions.html" );
				return;
			}
		}
		
		
		
		
		MediaArchive archive = getMediaArchive(inReq);
		UserSearcher searcher = (UserSearcher) archive.getSearcher("user");
		
		User target = searcher.getUserByEmail(email);
		if (autocreate && target == null)
		{
			target = (User) searcher.createNewData();
			target.setFirstName(firstname);
			target.setLastName(lastname);
			target.setEmail(email);
			target.setEnabled(true);
			searcher.saveData(target, null);			
		}
		if(target != null){
			
			inReq.putSessionValue(searcher.getCatalogId() + "user", target);
			String md5 = getCookieEncryption().getPasswordMd5(target.getPassword());
			String value = target.getUserName() + "md542" + md5;
			inReq.putPageValue("entermediakey", value);
			inReq.putPageValue( "user", target);
			if(getWebEventListener() != null)
			{
				WebEvent event = new WebEvent();
				event.setSearchType("userprofile");
				event.setCatalogId(searcher.getCatalogId());
				event.setOperation("userprofile/saved");
				event.setProperty("dataid", target.getId());
				event.setProperty("id", target.getId());

				event.setProperty("applicationid", inReq.findValue("applicationid"));

				getWebEventListener().eventFired(event);
			}


		}

	}

}
