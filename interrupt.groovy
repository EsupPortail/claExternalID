import org.apereo.cas.interrupt.InterruptResponse

import org.ldaptive.DefaultConnectionFactory
import org.ldaptive.ConnectionConfig
import org.ldaptive.AttributeModification
import org.ldaptive.ModifyOperation
import org.ldaptive.ModifyRequest
import org.ldaptive.ModifyResponse
import org.ldaptive.LdapAttribute
import org.ldaptive.BindConnectionInitializer
import org.ldaptive.ConnectionInitializer
import org.ldaptive.SearchOperation
import org.ldaptive.SearchRequest
import org.apereo.cas.configuration.CasConfigurationProperties

import java.text.SimpleDateFormat
import java.util.Calendar
import java.time.Duration
import javax.servlet.http.HttpServletRequest


def claExternalIDService() { return "https://localhost/claExternalID/associate" }
def block() { return false }
def ssoEnabled() { return true }

String getFirst(Object attributes, String name) {
    def vals = attributes.get(name)
    return vals == null ? null : vals[0]
}

// input format is YYYY-MM-DD
String to_LDAP_generalizedTime(String birthdate) {    
    return birthdate.replace("-", "") + "000000Z"
}

DefaultConnectionFactory ldaptive_connection(conf) {
	def cc = new ConnectionConfig()
	cc.setReconnectTimeout(Duration.ofSeconds(60))
	cc.setResponseTimeout(Duration.ofSeconds(60))
	cc.setLdapUrl(conf.ldapUrl)
	def bindConn = new BindConnectionInitializer(conf.bindDn, conf.bindCredential)
	ConnectionInitializer[] cnis = [bindConn]
	cc.setConnectionInitializers (cnis)

	return new DefaultConnectionFactory(cc)
}

ModifyResponse add_supannFCSub(conf, dcf, String fc_sub, String ldap_uid) {
    def requestModify = 
        new ModifyRequest("uid=${ldap_uid},${conf.baseDn}", 
            new AttributeModification(AttributeModification.Type.ADD, new LdapAttribute("supannFCSub", fc_sub)))
    return new ModifyOperation(dcf).execute(requestModify)
}

ModifyResponse remove_supannFCSub(conf, dcf, String fc_sub, ldap_uid) {
    def requestModify = 
        new ModifyRequest("uid=${ldap_uid},${conf.baseDn}", 
            new AttributeModification(AttributeModification.Type.DELETE, new LdapAttribute("supannFCSub", fc_sub)))
    return new ModifyOperation(dcf).execute(requestModify)
}

String getLDAPUserBirthdate(conf, String uid){
    def searchRequest = SearchRequest.builder()
        .dn(conf.baseDn)
        .filter("(uid=${uid})")
        .returnAttributes("up1BirthDay")
        .sizeLimit(2)
        .build()
    def response = new SearchOperation(ldaptive_connection(conf)).execute(searchRequest);
    return response.getEntry()?.getAttribute("up1BirthDay")?.getStringValue()
}

def removeTestsFcSub(conf) {
    def dcf = ldaptive_connection(conf)
    remove_supannFCSub(conf, dcf, "bb9efb98cd8d8dee7c1cfd7f3a2d7937fbbd09068b2ac4dce801abaa6eb8e6b4v1", "pldupont")
    remove_supannFCSub(conf, dcf, "ced88a7b04db5c2e2aefa09ac11966ce8f70502dcc40651b2d74e52fe49b97dfv1", "pldupont")
}

def force_LDAP_login(conf, logger, service, attributes, session) {

    logger.info("on sauvegarde les infos FranceConnect en session")
    session.setAttribute("target", service.getId());
    for (def attr in ["sub", "birthdate"]) {
        session.setAttribute(attr, getFirst(attributes, attr));
    }

    // => on force un cas/login avec le service claExternalID (donc avec FranceConnect non autorisé)
    // NB : le service https://localhost/claExternalID/associate n'est jamais atteint
    logger.info("on force cas/login avec le service claExternalID")

    // NB: convert from GString to String
    def redirect_url = "${conf.cas_prefix_name}/login?service=${claExternalIDService()}".toString()

    def interrupt_cla = new InterruptResponse("", [claExternalID : redirect_url], !block(), !ssoEnabled())
    interrupt_cla.setAutoRedirect(true)
    return interrupt_cla
}

def onlyFranceConnectSub(conf, logger, service, principal, attributes, session) {
    logger.info("onlyFranceConnectSub")

    def sub = getFirst(attributes, "sub")
    def given_name = getFirst(attributes, "given_name")
    def email = getFirst(attributes, "email")
    def gender = getFirst(attributes, "gender")
    def family_name = getFirst(attributes, "family_name")
    logger.warn("birthdate {}", getFirst(attributes, "birthdate"))
    def birthdate = to_LDAP_generalizedTime(getFirst(attributes, "birthdate"))

    logger.debug("attributs récupérés de FranceConnect [{}] [{}] [{}] [{}] [{}] [{}]",sub, given_name, email, gender, family_name, birthdate)

    def civiliteFilter = gender == "MALE" ?
        "(supannCivilite=M.)" :
        "(|(supannCivilite=Mlle)(supannCivilite=Mme))"
    def searchFilter = "(&(up1BirthDay=${birthdate})(up1BirthName=${family_name})(givenName=${given_name})(|(mail=${email})(supannMailPerso=${email}))${civiliteFilter})"
    
    def searchRequest = SearchRequest.builder()
        .dn(conf.baseDn)
        .filter(searchFilter)
        .returnAttributes("uid","displayName","sn","givenName","mail","eduPersonAffiliation","memberOf","labeledURI")
        .sizeLimit(2)
        .build()
    def dcf = ldaptive_connection(conf)
    def response = new SearchOperation(dcf).execute(searchRequest);

    logger.debug("resultat du filter {}: [{}]", searchFilter, response)

    if(response.entrySize() == 1) {
        // on a trouvé dans LDAP un utilisateur correspondant exactement. on appose supannFCSub
        def entry = response.getEntry()
        def uid = entry.getAttribute("uid")?.getStringValue()
        logger.info("LDAP exact match: add supannFCSub {} to {}", sub, uid)
        // TODO: interdire plusieurs supannFCSub ?
        ModifyResponse res = add_supannFCSub(conf, dcf, sub, uid)
        logger.debug("[{}]", res)

        logger.info("on ajoute les attributs LDAP aux attributs CAS")
        for (attr in entry.getAttributes()) {
            principal.attributes.put(attr.getName(), attr.getStringValues())
        }

        return res.isSuccess() ? 
            InterruptResponse.none() :  
            new InterruptResponse("",!block(), !ssoEnabled())
    } else {
        logger.info("pas d'utilisateur correspondant dans LDAP")
        return force_LDAP_login(conf, logger, service, attributes, session)
    }
}

def subInSession_and_ldapInAttrs(conf, logger, service, attributes, session) {
    logger.info("subInSession_and_ldapInAttrs")

    // on récupère les attributs FC en session
    def fc_sub = session.getAttribute("sub")
    def fc_birthdate = to_LDAP_generalizedTime(session.getAttribute("birthdate"))
    if (!fc_sub) throw new Exception("no sub in session")
    if (!fc_birthdate) throw new Exception("no birthdate in session")

    // on récupère l'uid dans les attributs de l'auth password LDAP
    def ldap_uid = getFirst(attributes, "uid")
    if (!ldap_uid) throw new Exception("no ldap_uid in attributes")
    logger.debug("ldap_uid [{}]", ldap_uid)

    def ldap_birthdate = getLDAPUserBirthdate(conf, ldap_uid)
    if(fc_birthdate != ldap_birthdate) {
        logger.warn("different birth dates {} != {}", fc_birthdate, ldap_birthdate)
        return new InterruptResponse("NOT ALLOWED", !block(), !ssoEnabled())
    }

    // => apposition du SUB FC
    def dcf = ldaptive_connection(conf)
    logger.info("User logged both FC & LDAP: add supannFCSub {} to {}", fc_sub, ldap_uid)
    // TODO: interdire plusieurs supannFCSub ?
    ModifyResponse res = add_supannFCSub(conf, dcf, fc_sub, ldap_uid)
    logger.debug("[{}]", res)

    if(res.isSuccess()) {
        def serviceInitial = session.getAttribute("target")
        logger.info("on remplace le faux service ${claExternalIDService()} par le service initial ${serviceInitial}")
        // on remplace le faux service claExternalIDService par le service initial
        service.setId(serviceInitial)
        service.setOriginalUrl(serviceInitial)

        return InterruptResponse.none()
    }
    return new InterruptResponse("", !block(), !ssoEnabled())
}

def get_conf(CasConfigurationProperties props) {
    return [
        ldapUrl: props.getAuthn().getLdap().get(0).getLdapUrl(),
        baseDn: props.getAuthn().getLdap().get(0).getBaseDn(),
        bindDn: props.getCustom().getProperties().get("claExternalID-ldap-bindDn"),
        bindCredential: props.getCustom().getProperties().get("claExternalID-ldap-bindCredential"),
        cas_prefix_name: props.getServer().getPrefix().toString(),
    ]
}

// NB: "service" est l'object correspondant au query param "service=xxx"
// NB: "registeredService" est l'entrée dans etc/cas/services/xxx correspondant au service demandé
def run(principal, attributes, service, registeredService, requestContext, logger, ...other_args) {
    try {
        final def session = ((HttpServletRequest)requestContext.getExternalContext().getNativeRequest()).getSession()
        final def props = requestContext.getActiveFlow().getApplicationContext().getBean(CasConfigurationProperties.class);
        final def conf = get_conf(props)

        logger.info("principal : [{}]", principal)
        logger.info("attributes : [{}]", attributes)
        //logger.info("service [{}]",service.originalUrl)
        //logger.info("registeredService : [{}]",registeredService)
        //logger.info("requestContext : [{}]",requestContext)
        logger.debug("attribute sub = [{}]", getFirst(attributes, "sub"))
        logger.info("session id = {}", session.getId())
        logger.info("session sub = {}", session.getAttribute("sub"))

        if (!service) {
            // on ne fait rien si pas de service (pour debug??)
            return InterruptResponse.none()
        } else if (service.originalUrl == 'http://localhost/integration-tests-cas-server/cleanup') {
            removeTestsFcSub(conf)
            return new InterruptResponse("test", !block(), !ssoEnabled())
        } else if (service.originalUrl == claExternalIDService()) {
            return subInSession_and_ldapInAttrs(conf, logger, service, attributes, session)
        } else if (getFirst(attributes, "uid")) {
            // soit l'utilisateur s'est authentifié sur LDAP, soit il s'est authentifié sur FranceConnect et le "sub" a été trouvé dans LDAP
            logger.debug("we have uid, no interrupt needed")
            return InterruptResponse.none()
        } else if (getFirst(attributes, "sub") && !getFirst(attributes, "uid")) {
            return onlyFranceConnectSub(conf, logger, service, principal, attributes, session)
        } else {
            throw new Exception("no sub nor uid")
        }
    } catch (err) {
        // NB par défaut les msg d'erreur ne sont pas loggués par CAS. On le fait explicitement et avec ce qui est utile :
        logger.error("{}", err.getMessage())
        for (def trace: err.getStackTrace()) {
            if (trace.getFileName()?.endsWith(".groovy"))
                logger.error("  {}", trace)
        }
    }
}
