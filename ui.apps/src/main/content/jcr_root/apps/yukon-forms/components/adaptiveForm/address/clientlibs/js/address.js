(function() {
    "use strict";

    const INIT_KEY = 'addressInputInitialized';

    function initAll(root) {
        (root || document).querySelectorAll(
            '[data-cmp-is="adaptiveFormAddressInput"] .cmp-adaptiveform-addressinput__widget'
        ).forEach(function(input) {
            new AddressInput(input);
        });
    }

    class AddressInput {
        // initializing input field with canada post api
        constructor(input) {
            if ($(input).data(INIT_KEY)) return;
            $(input).data(INIT_KEY, true);

            this.element = $(input).closest('.address_container');
            this.container = this.element.closest('.panel');
            if (this.container.length === 0) {
                this.container = this.element.closest('.rootPanel');
            }
            this.host = this.element.data("host");
            this.key = this.element.data("key");
            this.fixedLimit = this.element.data("limit") || 7;
            this.limit = this.fixedLimit;
            this.language = this.element.data("language") || "en";
            this.isFrench = this.language.includes("fr");
            this.language = this.isFrench ? "fr" : "en";
            this.usingRefinedSuggestions = false;
            this.fieldLine1    = this.element.data('field-line1')    || 'addressLine1';
            this.fieldLine2    = this.element.data('field-line2')    || 'addressLine2';
            this.fieldCity     = this.element.data('field-city')     || 'city';
            this.fieldProvince = this.element.data('field-province') || 'province';
            this.fieldPostalCode = this.element.data('field-postal-code') || 'postalCode';

            if (!this.host) {
                console.error("Invalid Canada Post HOST...")
                return;
            }
            // setting initial typeahead objects
            this.addressEngine = new Bloodhound({
                remote: {
                    url: this.getUrl(),
                    wildcard: '%QUERY'
                },
                datumTokenizer: Bloodhound.tokenizers.whitespace,
                queryTokenizer: Bloodhound.tokenizers.whitespace
            });
            this.widget = $(input);
            this.initTypeahead(this.addressEngine);
            this.addEventListeners();
        }

        // returns API url
        getUrl(params) {
            let text = params && params.text;
            let id = params && params.Id;
            let limit = params && params.limit;

            let url = this.host + "/addresscomplete/interactive/find/v2.10/json3.ws?";
            url += "Key=" + this.key;
            url += "&SearchTerm=" + (text || '%QUERY');
            url += "&LastId=" + (id || '');
            url += "&SearchFor=";
            url += "&Country=CAN";
            url += "&LanguagePreference=" + (this.language || 'en');
            url += "&MaxSuggestions=" + (limit || '');
            url += "&MaxResults=";
            url += "&Origin=";
            url += "&Bias=";
            url += "&Filter=";
            url += "&GeoFence=";
            return url;
        }

        // instantiating typeahead library with configuration
        initTypeahead(sourceEngine) {
            this.widget.typeahead('destroy');
            this.widget.typeahead(
                {
                    hint: false,
                    highlight: true,
                    minLength: 2
                },
                {
                    name: 'address-suggestions',
                    display: 'Text',
                    limit: this.limit,
                    source: sourceEngine,
                    templates: {
                        suggestion: function(data) {
                            if (data && (data.Text || data.Description)) {
                                data.Text = data.Text ? data.Text + ' ' : '';
                                return '<div><span>' + data.Text + data.Description + '</span></div>';
                            } else {
                                return '<div><span>' + data.Cause + '</span></div>';
                            }
                        }
                    }
                }
            );
        }

        // add click events when user selects on the dropdown
        addEventListeners() {
            const self = this;

            this.widget.on('typeahead:select', function(e, suggestion) {
                e.stopPropagation();
                e.stopImmediatePropagation();

                if (!suggestion.Id) {
                    return;
                }

                // when user selects on dropdown with multiple address
                if (suggestion.Next === "Find") {
                    let url = self.getUrl({Id: suggestion.Id, text: suggestion.Text, limit: 200})
                    $.ajax({
                        url: url,
                        dataType: 'json',
                        success: function(newResponse) {
                            const newSuggestions = newResponse.Items || [];
                            self.limit = newSuggestions.length;
                            self.initTypeahead(function(query, syncResults, asyncResults) {
                                syncResults(newSuggestions);
                            });
                            self.widget.typeahead('val', suggestion.Text);
                            self.widget.focus();
                            self.usingRefinedSuggestions = true;
                        },
                        error: function() {
                            console.log('Unable to find following address...');
                        }
                    });
                } else {
                    // for single address selection
                    $.ajax({
                        url: self.host + '/addresscomplete/interactive/retrieve/v2.11/json3.ws',
                        data: {
                            Key: self.key,
                            Id: suggestion.Id,
                            $cache: true
                        },
                        dataType: 'json',
                        success: function(response) {
                            let items = response.Items || [];
                            let targetLanguage = self.isFrench ? 'FRE' : 'ENG';
                            let address = items.find(function(item) {
                                return item.Language === targetLanguage;
                            });

                            if (!address && items.length > 0) {
                                address = items[0];
                            }
                            self.limit = self.fixedLimit;
                            self.container.find('.' + self.fieldLine1).find('input').val(address.Line1).blur();
                            self.container.find('.' + self.fieldLine2).find('input').val(address.Line2).blur();
                            self.container.find('.' + self.fieldCity).find('input').val(address.City).blur();
                            self.container.find('.' + self.fieldProvince).find('input').val(address.ProvinceCode).blur();
                            self.container.find('.' + self.fieldPostalCode).find('input').val(address.PostalCode).blur();
                            self.widget.val(address.Line1);
                            self.widget.blur();
                        },
                        error: function() {
                            console.log('Unable to find selected address...');
                        }
                    });
                }
            });

            // resets typeahead source after drilling into a multi-address result
            this.widget.on('blur', function() {
                if (self.usingRefinedSuggestions) {
                    self.usingRefinedSuggestions = false;
                    self.limit = self.fixedLimit;
                    self.initTypeahead(self.addressEngine);
                }
            });
        }
    }

    window.AddressInput = AddressInput;

    // Initialize inputs already in the DOM.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() { initAll(); });
    } else {
        initAll();
    }

    // Re-initialize whenever a new panel instance is added to a repeatable panel.
    new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            mutation.addedNodes.forEach(function(node) {
                if (node.nodeType === Node.ELEMENT_NODE) {
                    initAll(node);
                }
            });
        });
    }).observe(document.body, { childList: true, subtree: true });
})();
