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
    guidelib.i18n.strings = {

        "LostInternetConnection" : "Current functionality needs internet connectivity to work. Please connect your " +
            "device to the internet." , //Added in AEM 6.0 SP1
        "ESignDisabled" : "The privileges to sign the filled form are not available to you. Please, continue to the " +
            "next action or submit the form.",
        "VerifyDisabled" : "The privileges to verify the filled data are not available to you. Please, continue to " +
            "the next action or submit the form.",
        "validatingForm" : "Validating...",
        "submittingForm" : "Submitting...",
        "generatingSignAgreement" : "Preparing document for signature",
        "maxValErrorMessage" : "The value must be less than or equal to {0}",
        "exclusiveMaxValErrorMessage" : "The value must be strictly less than {0}",
        "minValErrorMessage" : "The value must be greater than or equal to {0}",
        "exclusiveMinValErrorMessage" : "The value must be strictly greater than {0}",
        "minimumLengthMessage" : "The number of characters must be greater than or equal to {0}",
        "totalLengthMessage" : "The number of characters must be equal to {0}",
        "totalDigitMessage" : "The number of digits must be less than or equal to {0}",
        "formAlreadySigned" : "The Form has been signed.",
        "formAlreadySubmitted" : "The Form has been submitted.",
        "datatypeMessage" : "The value must be of {0} datatype.",
        "genericInvalidDatePart": "A {0} can not have {1} than {2} {3}.",
        "genericInvalidYearPart": "Year can not have less than four digit.",
        "nonFebruaryMessage" : "{0} can not have more than {1} days.",
        "februaryMessage" : "February can not have more than {0} days in {1}.",
        "formHasBeenReset" : "The form has been reset.",
        "formSubmitFail" : "Failed to submit the form.",
        "formSaveError" : "Error while saving the form",
        "formSavedMessage" : "Form saved successfully"
    };
    /* Do not put any new message here. Use the guidelib.i18n.strings object */
    guidelib.i18n.LogMessages = {
        "AEM-AF-901-001"   :   "[AEM-AF-901-001]: Error in retrieving the form state.",
        "AEM-AF-901-003"   :   "[AEM-AF-901-003]: Unable to connect to the server.",
        "AEM-AF-901-004"   :   "[AEM-AF-901-004]: Encountered an internal error while submitting the form.",
        "AEM-AF-901-005"   :   "This field is mandatory.",
        "AEM-AF-901-006"   :   "There is a validation error in the field.",
        "AEM-AF-901-007"   :   "Field not filled in expected format.",
        "AEM-AF-901-008"   :   "Server is not reachable",
        "AEM-AF-901-009"   :   "Error occurred while draft saving",
        "AEM-AF-901-010"   :   "Verify works only with XFA-based Adaptive Forms.",
        "AEM-AF-901-011"   :   "Failed to restore the form state.",
        "AEM-AF-901-012"   :   "Failed to retrieve the form state.",
        "AEM-AF-901-013"   :   "User email undefined. Unable to generate signable PDF.",
        "AEM-AF-901-014"   :   "XDP title or Guide title undefined. Unable to generate signable PDF.",
        "AEM-AF-901-015"   :   "Error while submitting the guide: ",
        "AEM-AF-901-016"   :   "No signing field in the form. Please continue!",
        "AEM-AF-901-017"   :   "Failed to obtain data XML from HTML form: ",
        "AEM-AF-901-018"   :   "Please sign all the mandatory fields",
        "AEM-AF-901-019"   :   "Please esign the form.",
        "AEM-AF-901-020"   :   "Submitting the form...",
        "AEM-AF-901-021"   :   "Verify Component works only for XDP based DoR template.",
        "AEM-AF-901-022"   :   "Form submission failed due to incorrect field values. Correct the field values and resubmit the form.",
        "AEM-AF-901-023"   :   "The document is ready for signing but could not be loaded. We have sent the document to your email. Check your email to continue signing.",
        "AEM-AF-901-024"   :   "You cannot submit a form with empty attachment(s). Check these attachment(s) and resubmit the form: {0}",
        "AEM-AF-901-025"   :   "Google reCAPTCHA services are currently experiencing low availability. You can try again later after some time or contact your administrator if the issue continues."
    };
}(guidelib));
