import org.apereo.cas.interrupt.InterruptResponse

import org.apereo.cas.util.LdapUtils
import org.ldaptive.DefaultConnectionFactory
import org.ldaptive.SearchResponse
import org.ldaptive.SearchOperation
import org.ldaptive.SearchRequest
import org.ldaptive.ConnectionConfig
import org.ldaptive.ConnectionFactory
import org.ldaptive.AttributeModification
import org.ldaptive.ModifyOperation
import org.ldaptive.ModifyRequest
import org.ldaptive.ModifyResponse
import org.ldaptive.LdapException
import org.ldaptive.Connection
import org.ldaptive.LdapAttribute
import org.ldaptive.LdapEntry
import org.ldaptive.BindConnectionInitializer
import org.ldaptive.ConnectionInitializer
import org.apereo.cas.web.support.WebUtils
import org.apereo.cas.configuration.CasConfigurationProperties
import org.apereo.cas.authentication.principal.SimplePrincipal


import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.time.Duration
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession


String getBirthdate(Object birthdate) {
	String[] birthD = birthdate.split("-")
	Calendar calendar = Calendar.getInstance()
	int year = birthD[0].toInteger()
	int month = birthD[1].toInteger()
	int day = birthD[2].toInteger()
	calendar.set(year,month-1,day,0,0,0)
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss'Z'")
	return dateFormat.format(calendar.getTime())
}

boolean containsLdapAttrs(Object attrs) {
	return attrs.containsKey("givenName") && attrs.containsKey("up1BirthName") && attrs.containsKey("up1BirthDay") && attrs.containsKey("mail") && attrs.containsKey("supannMailPerso") && attrs.containsKey("supannCivilite")
}

String getLDAPUserBirthdate(String ldapUrl, String bindDn, String baseDn, String bindCredential, String uid){
	    def searchFilter = "(uid="+uid+")"
        DefaultConnectionFactory dcf = ldap_connect(ldapUrl, bindDn, bindCredential)
	    Connection connection = dcf.getConnection()
	    def result
		def up1BirthDay=null

	    try {
			connection.open()
			if (connection.isOpen()) {
				result =  LdapUtils.executeSearchOperation(dcf, baseDn, LdapUtils.newLdaptiveSearchFilter(searchFilter), 10, null)
				if(LdapUtils.containsResultEntry(result)) {
					LdapEntry entry = result.getEntry()
					up1BirthDay= LdapUtils.getString(entry, "up1BirthDay", null)
                    connection.close()
					return up1BirthDay
				}
			}
		}catch(LdapException e) {
		  logger.error("[{}]", e.getMessage())
		}finally {
		  connection.close()
		}

		return up1BirthDay
}

DefaultConnectionFactory ldap_connect(String url, String bindDn, String bindCredential) {
	ConnectionConfig cc = new ConnectionConfig()
	cc.setReconnectTimeout(Duration.ofSeconds(60))
	cc.setResponseTimeout(Duration.ofSeconds(60))
	cc.setLdapUrl(url)
	BindConnectionInitializer bindConn = new BindConnectionInitializer(bindDn, bindCredential)
	ConnectionInitializer[] cnis = [bindConn]
	cc.setConnectionInitializers (cnis)

	DefaultConnectionFactory dcf = new DefaultConnectionFactory(cc)
	return  dcf
}


def run(final Object... args) {
	def principal = args[0]
	def attributes = args[1]
	def service = args[2]
	def registeredService = args[3]
	def requestContext = args[4]
	def logger = args[5]

	def block = false
	def ssoEnabled = true

	def session = ((HttpServletRequest)requestContext.getExternalContext().getNativeRequest()).getSession()

	CasConfigurationProperties props= requestContext.getActiveFlow().getApplicationContext().getBean(CasConfigurationProperties.class);


	def ldapUrl = props.getAuthn().getLdap().get(0).getLdapUrl()
	def bindDn = props.getCustom().getProperties().get("claExternalID-ldap-bindDn")
    def bindCredential = props.getCustom().getProperties().get("claExternalID-ldap-bindCredential")
	def baseDn = props.getAuthn().getLdap().get(0).getBaseDn()

	def cas_server_name = props.getServer().getName()
	def cas_prefix_name = props.getServer().getPrefix()

	logger.info("[{}]",principal)
	logger.info("[{}]",attributes)
	logger.info("[{}]",requestContext)
	logger.info("[{}]",registeredService)

	String sub = ''
	if (service != null) {
		if(service.getAttributes().get("principal"))
			sub=service.getAttributes().get("principal").toString().replace("[","").replace("]","")
		else if(principal.getAttributes().get("sub"))
			sub=principal.getAttributes().get("sub").toString().replace("[","").replace("]","")
		logger.info("[{}]",service)
	}

	logger.debug("sub = [{}]", sub)

	if(!attributes.containsKey("uid") && service) {

			//Implementer le traitement effectué par le module claExternalID
			def id = attributes.get("sub").toString().replace("[","").replace("]","")
			def given_name = attributes.get("given_name").toString().replace("[","").replace("]","")
			def email = attributes.get("email").toString().replace("[","").replace("]","")
			def gender = attributes.get("gender").toString().replace("[","").replace("]","")
			def family_name = attributes.get("family_name").toString().replace("[","").replace("]","")
			String birthdate = attributes.get("birthdate").toString().replace("[","").replace("]","")

			birthdate= getBirthdate(birthdate)

			logger.debug("[{}] [{}] [{}] [{}] [{}] [{}]",id, given_name, email, gender, family_name, birthdate)

			def searchFilter=""

			if(gender.equals("MALE")){
				searchFilter = "(&(up1BirthDay="+birthdate+")(|(mail="+email+")(supannMailPerso="+email+"))(supannCivilite=M.)(up1BirthName="+family_name+")(givenName="+given_name+"))"
			  }else{
				searchFilter = "(&(up1BirthDay="+birthdate+")(|(mail="+email+")(supannMailPerso="+email+"))(|(supannCivilite=Mlle)(supannCivilite=Mme))(up1BirthName="+family_name+")(givenName="+given_name+"))"
			  }

			String[] returnAttrs = ["uid","displayName","sn","givenName","mail","eduPersonAffiliation","memberOf","labeledURI"]

			DefaultConnectionFactory dcf = ldap_connect(ldapUrl, bindDn, bindCredential)
			Connection connection = dcf.getConnection()
			def result

			try {
				connection.open()
				if (connection.isOpen()) {
					result =  LdapUtils.executeSearchOperation(dcf, baseDn, LdapUtils.newLdaptiveSearchFilter(searchFilter), 10, returnAttrs)

					logger.debug("[{}]",result)

					if(LdapUtils.containsResultEntry(result)) {
						//apposition du SUB FC
						LdapEntry entry = result.getEntry()
						def uid= LdapUtils.getString(entry, "uid", null)
						logger.debug("[{}]", uid)
						ModifyOperation modify = new ModifyOperation(dcf)
						LdapAttribute attr = new LdapAttribute("supannFCSub", sub);
						ModifyRequest requestModify = new ModifyRequest("uid="+uid+","+baseDn, new AttributeModification(AttributeModification.Type.ADD, attr))
						ModifyResponse res = modify.execute(requestModify)
						logger.debug("[{}]", res)
						try{
							for(returnAttr in returnAttrs)
								  principal.attributes.put(returnAttr,[LdapUtils.getString(entry, returnAttr, null)])
						}catch(NullPointerException e){ logger.error("[{}]", e.getMessage())}
						if(res.isSuccess()) {
							return new InterruptResponse("", block, ssoEnabled)
						}
					}else {
						session.setAttribute("sub", sub);
						session.setAttribute("target", service.getId());
						session.setAttribute("given_name", given_name);
						session.setAttribute("email", email);
						session.setAttribute("gender", gender);
						session.setAttribute("family_name", family_name);
						session.setAttribute("birthdate", birthdate);
						logger.debug("Session Object [{}]", session.getAttributeNames())
						def e = session.getAttributeNames()
						while(e.hasMoreElements()){
							logger.debug("Session attribute name [{}]", e.nextElement());
						}
						InterruptResponse interrupt_cla = new InterruptResponse("",[claExternalID  : cas_prefix_name+"/login?service=https://localhost/claExternalID/associate"], !block, !ssoEnabled)
						interrupt_cla.setAutoRedirect(true)
						return interrupt_cla
					}
				}
			}catch(LdapException e) {
					logger.error("[{}]", e.getMessage())
			}
			connection.close()
			return new InterruptResponse("",!block, !ssoEnabled)
	}else if (registeredService) {
		if(registeredService.getName().equals("Bonjour")) {
			def e = session.getAttributeNames()
			while(e.hasMoreElements()){
				String attributeName=e.nextElement()
				logger.debug("Session attribute name [{}={}]", attributeName,session.getAttribute(attributeName))
			}

			def fc_sub =  session.getAttribute("sub")
			def fc_family_name = session.getAttribute("family_name")
			def fc_birthdate = session.getAttribute("birthdate")
			def ldap_uid = attributes.get("uid").toString().replace("[","").replace("]","")
			logger.debug("[{}]", ldap_uid)
			def ldap_birthdate = getLDAPUserBirthdate(ldapUrl, bindDn, baseDn,  bindCredential, ldap_uid)

			service.setId(session.getAttribute("target"))
			service.setOriginalUrl(session.getAttribute("target"))

			if(fc_birthdate.equals(ldap_birthdate)) {
				DefaultConnectionFactory dcf = ldap_connect(ldapUrl, bindDn, bindCredential)
				Connection connection = dcf.getConnection()
				try {
					connection.open()
					if (connection.isOpen()) {
						def uid = attributes.get("uid").toString().replace("[","").replace("]","")
						logger.debug("[{}]", uid)
						ModifyOperation modify = new ModifyOperation(dcf)
						LdapAttribute attr = new LdapAttribute("supannFCSub", fc_sub);
						ModifyRequest requestModify = new ModifyRequest("uid="+uid+","+baseDn, new AttributeModification(AttributeModification.Type.ADD, attr))
						ModifyResponse res = modify.execute(requestModify)
						logger.debug("[{}]", res)
						logger.debug("[{}]", service)
						if(res.isSuccess()) {
							InterruptResponse interrupt =  new InterruptResponse("", block, ssoEnabled)
							return interrupt
						}
					}
				}catch(LdapException ex) {
					logger.error("[{}]", ex.getMessage())
				}
				return new InterruptResponse("", !block, !ssoEnabled)
			}else {
				return new InterruptResponse("NOT ALLOWED", !block, !ssoEnabled)
			}
		}else { return InterruptResponse.none() }
	} else {
		return InterruptResponse.none()
	}
}
