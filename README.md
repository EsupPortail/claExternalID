# claExternalID
claExternalID (CAS LDAP Associate External ID) est un outil qui permet d'enregistrer un identifiant externe sur le profil LDAP d'un utilisateur authentifié avec CAS.

## Intégration dans CAS

Vous aurez besoin de placer le script groovy à l'emplacement suivant : **$CAS_HOME/etc/cas/config/mon_script.groovy**

et d'ajouter la propriéte suivante dans le fichier de propriété de CAS :

```
cas.interrupt.groovy.location=$CAS_HOME/etc/cas/config/interrupt.groovy
```

Comme vous aurez aussi besoin d'ajouter la dépendance suivante dans le fichier build.gradle pour activer la fonctionnalité permettant l'usage de cet outil :

```
implementation "org.apereo.cas:cas-server-support-interrupt-webflow:${project.'cas.version'}"
```

**$CAS_HOME** étant le repertoire d'installation de votre CAS-overlay-template

### Exemple d'autorisation du service dans CAS

Attention : préciser l'expression regulière associée à la propriété serviceId, en fonction du nom de domaine du serveur CAS.

Créer le fichier suivant dans votre dossier services/ ou via l'application Service Management application.

Fichier claExternalID_Associate-55.json
``` json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "https://localhost/claExternalID/associate",
  "name" : "Bonjour",
  "id" : 55,
  "description" : "Nous n'avons pas réussi à vous retrouver parmi nos utilisateurs.\nSi vous êtes étudiant ou personnel de l'université Paris 1 Panthéon-Sorbonne, veuillez vous authentifier. Cette opération est à réaliser une fois.\nSi vous n'êtes pas étudiant ou personnel de Paris 1, vous n'êtes pas autorisé à accéder à ce service. Veuillez cliquer sur \"Annuler\" pour vous déconnecter de FranceConnect",
  "evaluationOrder" : 55,
  "usernameAttributeProvider" : {
    "@class" : "org.apereo.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider",
    "usernameAttribute" : "uid"
  },
  "accessStrategy": {
     "@class" : "org.apereo.cas.services.DefaultRegisteredServiceAccessStrategy",
     "requiredAttributes" : {
	    "@class" : "java.util.HashMap",
	    "uid" : [ "java.util.HashSet", [ ".*" ] ]
     },
     "delegatedAuthenticationPolicy" : {
        "@class" : "org.apereo.cas.services.DefaultRegisteredServiceDelegatedAuthenticationPolicy",
        "allowedProviders" : [ "java.util.ArrayList", ["AUCUN"] ]
     }     
  }
}
```

De plus, vous aurez finalement besoin de configurer CAS afin que celui-ci retrouve votre utilisateur lors de la seconde 
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

#Usage d'un bind applicatif dédié
cas.custom.properties.claExternalID-ldap-bindDn=......
cas.custom.properties.claExternalID-ldap-bindCredential=**************


# Identities reconciliation
cas.authn.attributeRepository.ldap[0].ldapUrl=ldap://ldap.univ.fr
cas.authn.attributeRepository.ldap[0].useSsl=false
cas.authn.attributeRepository.ldap[0].baseDn=ou=users,dc=univ,dc=fr
# To retrieve LDAP data fill in values unless your ACL permits you to do it without.
cas.authn.attributeRepository.ldap[0].bindDn=
cas.authn.attributeRepository.ldap[0].bindCredential=
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


## Fonctionnement technique sous les versions 6.x de CAS

L'essentiel de cet outil repose enrtièrement sur le mechanisme d'interruption mis à dispostion par CAS, qui permet de reprendre la même cinématique d'éxécution que celle des versions antérieurs :
- CAS vérifie si, après l'authentification OIDC, que l'UID est présent
    - Sinon, vérifie si il est possible de procéder à une réconciliation automatique des identités :
        - Si oui, renvoi vers le service demandé
        - Sinon procéde à une tentative de réconciliation manuelle des identités :
            - Si réussie, renvoi vers le service demandé
            - Sinon bloque l'accès au service
    - Si oui, renvoi vers le service demandé

## Interface graphique

La fonctionnalité regissant l'interruption des authentifctaions offerte par CAS, génére lors de l'éxécution une vue spécifique et dont le contenue s'adapte en fonction de l'état de l'authentification déclenchée,
si besoin vous pourrez modifier l'aspect de cette dernière pour qu'elle corresponde mieux à vos besoins.

ci-dessous la commande à taper pour accèder à cette vue :
```
./gradlew[.bat] getResource -PresourceName=casInterruptView.html
```





