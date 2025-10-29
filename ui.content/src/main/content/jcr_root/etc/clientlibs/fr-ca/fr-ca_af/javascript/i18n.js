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

    // Define all locale-specific formatting, symbols, calendar strings, etc.
    guidelib.i18n["fr-CA"] = {
        "calendarSymbols": {
            "monthNames": [
                "janvier",
                "février",
                "mars",
                "avril",
                "mai",
                "juin",
                "juillet",
                "août",
                "septembre",
                "octobre",
                "novembre",
                "décembre"
            ],
            "abbrmonthNames": [
                "janv.",
                "févr.",
                "mars",
                "avr.",
                "mai",
                "juin",
                "juil.",
                "août",
                "sept.",
                "oct.",
                "nov.",
                "déc."
            ],
            "dayNames": [
                "dimanche",
                "lundi",
                "mardi",
                "mercredi",
                "jeudi",
                "vendredi",
                "samedi"
            ],
            "abbrdayNames": [
                "dim.",
                "lun.",
                "mar.",
                "mer.",
                "jeu.",
                "ven.",
                "sam."
            ],
            "meridiemNames": [
                "AM",
                "PM"
            ],
            "eraNames": [
                "av. J.-C.",
                "ap. J.-C."
            ],
            "day": "jour",
            "days": "jours",
            "month": "mois",
            "months": "mois",
            "year": "année",
            "years": "années",
            "more": "plus",
            "less": "moins"
        },

        "datePatterns": {
            "full":  "EEEE D MMMM YYYY",
            "long":  "D MMMM YYYY",
            "med":   "D MMM YY",
            "short": "DD/MM/YY"
        },

        "timePatterns": {
            "full":  "HH' h 'MM Z",
            "long":  "HH:MM:SS Z",
            "med":   "HH:MM:SS",
            "short": "HH:MM"
        },

        "dateTimeSymbols": "GaMjkHmsSEDFwWxhKzZ",

        "numberPatterns": {
            "numeric":  "z,zz9.zzz",
            "currency": "z,zz9.99 $",
            "percent":  "z,zz9%"
        },

        "numberSymbols": {
            "decimal":  ",",
            "grouping": " ",
            "percent":  "%",
            "minus":    "-",
            "zero":     "0"
        },

        // TODO: UPDATE AFTER TESTING
        "currencySymbols": {
            "CAD": "&",
            "USD": "&",
            "EUR": "&"
        },

        "typefaces": {}
    };

}(window.guidelib = window.guidelib || {}));
