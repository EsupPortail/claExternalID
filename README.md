# claExternalID
claExternalID (CAS LDAP Associate External ID) est un outil qui permet d'enregistrer un identifiant externe sur le profil LDAP d'un utilisateur authentifié avec CAS.

CAS doit être utilisé en HTTPS afin de faire fonctionner le module.

## Configuration

src/main/webapp/WEB-INF/config.json 

## Build

`mvn compile`

## Deployment

Copier target/claExternalID.war dans votre serveur d'application (dossier webapps sous tomcat).

ou 

Run le serveur jetty en local simplement :
`mvn -Djetty.port=8082 compile jetty:run`

## Intégration dans CAS

Vous aurez besoin d'installer [EsupPortail/cas-server-support-claExternalID](https://github.com/EsupPortail/cas-server-support-claExternalID) dans votre application CAS afin de modifier le comportement et ainsi de recevoir les paramètres de l'OIDC.

### Exemple d'autorisation du service dans CAS

Attention : sur un environnement de production, préciser l'expression regulière associée à la propriété serviceId.

Créer le fichier suivant dans votre dossier services/ ou via l'application Service Management application.

Fichier claExternalID_Associate-55.json
``` json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "^https?://.*/claExternalID/associate/.*",
  "name" : "Votre identité FranceConnect n'est pas connue dans l'établissement",
  "id" : 55,
  "description" : "Veuillez vous authentifier auprès de l'université pour confirmer votre identité",
  "evaluationOrder" : 55,
  "usernameAttributeProvider" : {
      "@class" : "org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider",
      "usernameAttribute" : "uid"
  },
  "accessStrategy": {
      "@class" : "org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy",
      "delegatedAuthenticationPolicy" : {
          "@class" : "org.apereo.cas.services.DefaultRegisteredServiceDelegatedAuthenticationPolicy",
          "allowedProviders" : [ "java.util.ArrayList", [""] ]
      }
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

Vous aurez aussi besoin de modifier le comportement de votre fichier de service par défaut.
Fichier ALL-100.json

``` json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "^(https|imaps)://.*",
  "name" : "Identifiez vous",
  "id" : 100,
  "description" : "Bienvenue sur notre site",
  "evaluationOrder" : 100,
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
Ce fichier forcera le service `^(https|imaps)://.*` à verifier la présence de l'argument `uid` lors du renvoi 
des paramètres de l'OIDC et autre `attributeRepository`, si ce n'est le cas (ne devrait pas à la première 
authentification), CAS renvoie vers `unauthorizedRedirectUrl`. 

De plus, vous aurez finalement besoin de configurer CAS afin que celui retrouve votre utilisateur lors de la seconde 
authentification :

``` properties
# LDAP
cas.authn.ldap[0].type=ANONYMOUS
cas.authn.ldap[0].useSsl=false
cas.authn.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.ldap[0].baseDn=ou=users,dc=univ,dc=fr
cas.authn.ldap[0].dnFormat=ou=users,dc=univ,dc=fr
cas.authn.ldap[0].searchFilter=cn={user}

cas.authn.ldap[0].principalAttributeList=uid

cas.authn.attributeRepository.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.attributeRepository.ldap[0].useSsl=false
cas.authn.attributeRepository.ldap[0].baseDn=ou=users,dc=univ,dc=fr
cas.authn.attributeRepository.ldap[0].searchFilter=(supannRefId={FranceConnect}{user})

# Must be the same as usernameAttributeProvider
cas.authn.attributeRepository.ldap[0].attributes.uid=uid
# Optional, depending on your settings
cas.authn.attributeRepository.ldap[0].attributes.cn=cn

# Display attributes
cas.authn.attributeRepository.defaultAttributesToRelease=uid,cn
```

Enlever le paramètre de cache. Vu que plusieurs requêtes sont faites à des moments différents lors de l'authentification, le cache pose problème pour le rafraîchissement des attributs qui sont mis à jour par notre module.
```properties
cas.authn.attributeRepository.expirationTime=0
```


## Fonctionnement technique

Disons que notre claExternalId a comme adresse `https://my-jetty-server.com:8082/claExternalID/`
- CAS vérifie si, après l'authentification OIDC, que l'UID est présent
  - Si non, renvoi vers l'URL du serveur claExternalID
  - Si oui, renvoi vers le service demandé

- Arrivé sur `https://my-jetty-server.com:8082/claExternalID/`, enregistre en session l'ID OIDC.
- Renvoi vers `https://my-jetty-server.com:8082/claExternalID/associate`, celui-ci demandera une authentification CAS via le login Form.
- L'identification se fait sur CAS et renvoie vers `https://my-jetty-server.com:8082/claExternalID/associate` qui lie l'ID OIDC avec le UID.
- Renvoi vers le service d'origine demandé lors de la première authentification.

## TO DO

- Integrate this module to the CAS WEBFLOW.
