# claExternalID
claExternalID (CAS LDAP Associate External ID) est un outil qui permet d'enregistrer un identifiant externe sur le profil LDAP d'un utilisateur authentifié avec CAS

CAS doit etre utilisé en HTTPS afin de faire fonctionner le module

## Configuration

src/main/webapp/WEB-INF/config.json 

## Build

`mvn compile`

## Deployment

Copier target/claExternalID.war dans votre serveur d'application (dossier webapps sous tomcat)

ou 

Run le serveur jetty en local simplement :
`mvn -Djetty.port=8082 compile jetty:run`

## Integration dans CAS

Vous aurez besoin d'installer [EsupPortail/cas-server-support-claExternalID](https://github.com/EsupPortail/cas-server-support-claExternalID) dans votre application CAS afin de modifier le comportement et ainsi de recevoir les parametres de l'OIDC.

### Exemple d'autorisation du service dans CAS

Attention : sur un environnement de production, préciser l'expression regulière associée à la propriété serviceId.

Créer ce fichier suivants dans votre dossier services/ ou via l'application Service Management application

Fichier claExternalID_Associate-55.json
``` json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "^https?://.*/claExternalID/associate/.*",
  "name" : "Votre identité FranceConnect n'est pas connu dans l'établissement",
  "id" : 55,
  "theme": "cla",
  "description" : "Veuillez vous authentifier auprès de l'université pour confirmer votre identité",
  "evaluationOrder" : 55,
  "usernameAttributeProvider" : {
    "@class" : "org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider",
    "usernameAttribute" : "uid"
  },
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.ReturnAllAttributeReleasePolicy",
    "principalAttributesRepository" : {
      "@class" : "org.apereo.cas.authentication.principal.cache.CachingPrincipalAttributesRepository",
      "mergingStrategy" : "ADD"
    }
  }
}
```

Vous aurez aussi besoin de modifier le comportement de votre fichier de service par default :

``` json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "^(https|imaps)://.*",
  "name" : "Identifiez vous",
  "id" : 9997,
  "description" : "Bienvenue sur notre site",
  "evaluationOrder" : 9998,
  "usernameAttributeProvider" : {
    "@class" : "org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider",
    "usernameAttribute" : "uid"
  },
  "accessStrategy": {
    "@class" : "org.esupportail.cas.services.ClaExternalIDRegisteredServiceAccessStrategy",
    "unauthorizedRedirectUrl" : "https://my-jetty-server.com:8082/claExternalID/",
    "requiredAttributes" : {
	    "@class" : "java.util.HashMap",
	    "uid" : [ "java.util.HashSet", [ ".*" ] ]
	  }
  }
}
```
Ce fichier forcera le service `^(https|imaps)://.*` a verifier la présence de l'argument `uid` lors du renvoi 
des parametres de l'OIDC et autre `attributeRepository`, si ce n'est le cas (ne devrait pas à la premiere 
autentification), CAS renvoi vers `unauthorizedRedirectUrl`. 

De plus, vous aurez finalement besoin de configurer CAS afin que celui retrouve votre utilisateur lors de la seconde 
autentification :

``` properties
# LDAP
cas.authn.ldap[0].type=ANONYMOUS
cas.authn.ldap[0].useSsl=false
cas.authn.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.ldap[0].baseDn=ou=users,dc=univ,dc=fr
cas.authn.ldap[0].dnFormat=ou=users,dc=univ,dc=fr
cas.authn.ldap[0].userFilter=cn={user}

cas.authn.ldap[0].principalAttributeList=uid

cas.authn.attributeRepository.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.attributeRepository.ldap[0].useSsl=false
cas.authn.attributeRepository.ldap[0].baseDn=ou=users,dc=univ,dc=fr
cas.authn.attributeRepository.ldap[0].userFilter=(supannRefId={FranceConnect}{user})

# Must be the same as usernameAttributeProvider
cas.authn.attributeRepository.ldap[0].attributes.uid=uid
# Optional, depending on your settings
cas.authn.attributeRepository.ldap[0].attributes.cn=cn

# Display attributes
cas.authn.attributeRepository.defaultAttributesToRelease=uid,cn
```

Enlever le paramétre de cache. Vu que plusieurs requetes sont faites à moment different lors de l'authentification, le cache pose probleme pour le rafraichissement 
des attributs qui sont mis à jour par notre module.
```properties
cas.authn.attributeRepository.expireInMinutes=0
```

Afin de pouvoir s'identifier au prealable sur FranceConnect à d'autre service du meme domaine, claExternalID a besoin de logout
l'utilisateur via l'adresse "https://my_cas_example.com/cas/logout?service=https://my_claExternalID_example.com/claExternalID/associate..."
Nous devons activer l'option dans CAS
```properties
cas.logout.followServiceRedirects=true
```

## Fonctionnement technique

Disons que notre claExternalId a comme adresse `https://my-jetty-server.com:8082/claExternalID/`
- CAS verifie si apres l'authentification OIDC que l'UID est present
  - Si non, renvoi vers l'URL du serveur claExternalID
  - Si oui, renvoi vers le service demandé

- Arrivé sur `https://my-jetty-server.com:8082/claExternalID/`, enregistre en session l'ID OIDC.
- Renvoi vers `https://my-jetty-server.com:8082/claExternalID/associate`, celui-ci demandera une autentification CAS via le login Form.
- L'identification se fait sur cas et renvoi vers `https://my-jetty-server.com:8082/claExternalID/associate` qui lie l'ID OIDC avec le UID.
- Renvoi vers le service d'origine demandé lors de la première autentification.

## TO DO

- Integrate this module to the CAS WEBFLOW
