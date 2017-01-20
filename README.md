# claExternalID
claExternalID (CAS LDAP Associate External ID) est un outil qui permet d'enregistrer un identifiant externe sur le profil LDAP d'un utilisateur authentifié avec CAS

### Exemple d'autorisation du service dans CAS

Créer les deux fichiers suivants dans src/main/resources/services/ puis packager votre CAS

fichier claExternalID-75.json

	{
	  "@class" : "org.jasig.cas.services.RegexRegisteredService",
	  "serviceId" : "^https?://.*/claExternalID/.*",
	  "name" : "claExternalID",
	  "id" : 75,
		  "description" : "Retrieve external identifier and redirect to save it into user profile",
	  "evaluationOrder" : 75,
	  "attributeReleasePolicy" : {
	    "@class" : "org.jasig.cas.services.ReturnAllAttributeReleasePolicy",
	    "principalAttributesRepository" : {
		      "@class" : "org.jasig.cas.authentication.principal.cache.CachingPrincipalAttributesRepository",
      "mergingStrategy" : "ADD"
      }
	  },
	  "accessStrategy" : {
	    "@class" : "org.jasig.cas.services.DefaultRegisteredServiceAccessStrategy",
	    "enabled" : true,
	    "ssoEnabled" : true
	  }
	}


fichier claExternalID-Associate-55.json

	{
	  "@class" : "org.jasig.cas.services.RegexRegisteredService",
	  "serviceId" : "^https?://.*/claExternalID/associate/.*",
	  "name" : "Votre identité FranceConnect n'est pas connu dans l'établissement",
	  "id" : 55,
	  "theme":"claExternalID",
		  "description" : "Veuillez vous authentifier auprès de l'université pour confirmer votre identité",
	  "evaluationOrder" : 55,
	  "usernameAttributeProvider" : {
		    "@class" : "org.jasig.cas.services.PrincipalAttributeRegisteredServiceUsernameProvider",
    "usernameAttribute" : "uid"
	  },
	  "attributeReleasePolicy" : {
	    "@class" : "org.jasig.cas.services.ReturnAllAttributeReleasePolicy",
	    "principalAttributesRepository" : {
		      "@class" : "org.jasig.cas.authentication.principal.cache.CachingPrincipalAttributesRepository",
      "mergingStrategy" : "ADD"
      }
	  },
	"accessStrategy" : {
    "@class" : "org.jasig.cas.services.DefaultRegisteredServiceAccessStrategy",
    "enabled" : true,
    "ssoEnabled" : false
	  }
	}


