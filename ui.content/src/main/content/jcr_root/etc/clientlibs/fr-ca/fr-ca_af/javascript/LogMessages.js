/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2014 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/

(function (guidelib) {
    "use strict";

    guidelib.i18n = guidelib.i18n || {};
    guidelib.i18n.strings = guidelib.i18n.strings || {};
    guidelib.i18n.LogMessages = guidelib.i18n.LogMessages || {};

    // User-facing inline validation / status strings
    guidelib.i18n.strings["fr-CA"] = {
        "LostInternetConnection": "Connectivité Internet requise pour la fonctionnalité actuelle. Connectez votre périphérique à Internet.",
        "ESignDisabled": "Vous ne disposez pas des droits vous permettant de signer le formulaire rempli. Passez à l’action suivante ou envoyez le formulaire.",
        "VerifyDisabled": "Vous ne disposez pas des droits vous permettant de vérifier les données indiquées. Passez à l’action suivante ou envoyez le formulaire.",
        "validatingForm": "Validation en cours...",
        "submittingForm": "Envoi en cours...",
        "generatingSignAgreement": "Préparation du document pour la signature",
        "maxValErrorMessage": "La valeur doit être inférieure ou égale à {0}",
        "exclusiveMaxValErrorMessage": "La valeur doit être strictement inférieure à {0}",
        "minValErrorMessage": "La valeur doit être supérieure ou égale à {0}",
        "exclusiveMinValErrorMessage": "La valeur doit être strictement supérieure à {0}",
        "minimumLengthMessage": "Le nombre de caractères doit être supérieur ou égal à {0}",
        "totalLengthMessage": "Le nombre de caractères doit être égal à {0}",
        "totalDigitMessage": "Le nombre de chiffres doit être inférieur ou égal à {0}",
        "formAlreadySigned": "Le formulaire a été signé.",
        "formAlreadySubmitted": "Le formulaire a été soumis.",
        "datatypeMessage": "Le type de données de la valeur doit être {0}.",
        "genericInvalidDatePart": "Un {0} ne peut pas avoir plus de {1} que {2} {3}.",
        "genericInvalidYearPart": "L’année ne peut pas comporter moins de quatre chiffres.",
        "nonFebruaryMessage": "{0} ne peut pas avoir plus de {1} jours.",
        "februaryMessage": "Février ne peut pas avoir plus de {0} jours en {1}.",
        "formHasBeenReset": "Le formulaire a été réinitialisé.",
        "formSubmitFail": "Échec de l’envoi du formulaire.",
        "formSaveError": "Erreur lors de l’enregistrement du formulaire",
        "formSavedMessage": "Formulaire enregistré"
    };

    // System / logging / error codes
    guidelib.i18n.LogMessages["fr-CA"] = {
        "AEM-AF-901-001": "[AEM-AF-901-001]: Erreur de récupération de l'état du formulaire.",
        "AEM-AF-901-003": "[AEM-AF-901-003]: Connexion au serveur impossible.",
        "AEM-AF-901-004": "[AEM-AF-901-004]: Une erreur interne s'est produite lors de l'envoi du formulaire.",
        "AEM-AF-901-005": "Ce champ est obligatoire.",
        "AEM-AF-901-006": "Erreur de validation dans le champ.",
        "AEM-AF-901-007": "Champ non renseigné dans le format attendu.",
        "AEM-AF-901-008": "Serveur inaccessible",
        "AEM-AF-901-009": "Erreur lors de l’enregistrement des versions préliminaires",
        "AEM-AF-901-010": "La vérification fonctionne uniquement avec les formulaires adaptatifs XFA.",
        "AEM-AF-901-011": "Échec de restauration de l’état du formulaire.",
        "AEM-AF-901-012": "Échec de récupération de l’état du formulaire.",
        "AEM-AF-901-013": "Adresse électronique de l’utilisateur non définie. Impossible de générer le PDF à signer.",
        "AEM-AF-901-014": "Titre XDP ou titre du guide non défini. Impossible de générer le PDF à signer.",
        "AEM-AF-901-015": "Erreur lors de l’envoi du guide : ",
        "AEM-AF-901-016": "Aucun champ de signature dans le formulaire. Veuillez continuer.",
        "AEM-AF-901-017": "Échec de l’obtention des données XML du formulaire HTML : ",
        "AEM-AF-901-018": "Signez tous les champs obligatoires.",
        "AEM-AF-901-019": "Signez électroniquement le formulaire.",
        "AEM-AF-901-020": "Envoi du formulaire...",
        "AEM-AF-901-021": "Le composant Vérifier fonctionne uniquement pour un modèle DE basé sur XDP.",
        "AEM-AF-901-022": "Échec de l’envoi du formulaire en raison de valeurs de champ incorrectes. Corrigez les valeurs des champs et renvoyez le formulaire.",
        "AEM-AF-901-023": "Le document est prêt pour signature, mais n’a pas pu être chargé. Nous vous l’avons envoyé par e-mail. Vérifiez vos e-mails pour effectuer la signature.",
        "AEM-AF-901-024": "Vous ne pouvez pas soumettre un formulaire avec des pièces jointes vides. Vérifiez la ou les pièces jointes suivantes et soumettez à nouveau le formulaire : {0}",
        "AEM-AF-901-025": "Les services Google reCAPTCHA ont actuellement une faible disponibilité. Vous pourrez réessayer ultérieurement ou contacter votre administrateur ou administratrice si le problème persiste."
    };

}(window.guidelib = window.guidelib || {}));
