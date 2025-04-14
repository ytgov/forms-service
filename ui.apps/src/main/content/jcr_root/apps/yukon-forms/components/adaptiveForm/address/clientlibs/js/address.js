(function() {
    "use strict";
    class AddressInput {
        static bemBlock = 'cmp-adaptiveform-addressinput';
        static selectors = {
            widget: `.${AddressInput.bemBlock}__widget`
        };

        // initializing input field with canada post api
        constructor(params) {
            this.element = params.element;
            this.container = params.formContainer;
            this.host = $(this.element).data("host");
            this.key = $(this.element).data("key");
            this.fixedLimit = $(this.element).data("limit") || 7;
            this.limit = this.fixedLimit;
            this.language = $(this.element).data("language") || "en";
            this.isFrench = this.language.includes("fr");
            this.language = this.isFrench ? "fr" : "en";
            this.usingRefinedSuggestions = false;

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
            this.widget = this.getWidget();
            this.initTypeahead(this.addressEngine);
            this.addEventListeners();
        }

        // returns input field
        getWidget() {
            return $(this.element).find(AddressInput.selectors.widget).get(0);
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
            $(this.widget).typeahead('destroy');
            $(this.widget).typeahead(
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

            $(this.widget).on('typeahead:select', function(e, suggestion) {
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
                            $(self.widget).typeahead('val', suggestion.Text);
                            $(self.widget).focus();
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
                            $(self.container).find('[data-name="address2"]').val(address.Line2).blur();
                            $(self.container).find('[data-name="city"]').val(address.City).blur();
                            $(self.container).find('[data-name="province"]').val(address.ProvinceCode).blur();
                            $(self.container).find('[data-name="postalCode"]').val(address.PostalCode).blur();
                            $(self.widget).val(address.Line1);
                            $(self.widget).blur();
                        },
                        error: function() {
                            console.log('Unable to find selected address...');
                        }
                    });
                }
            });

            // resets input fields when interaction is completed
            $(this.widget).on('blur', function() {
                if (self.usingRefinedSuggestions) {
                    self.usingRefinedSuggestions = false;
                    self.limit = self.fixedLimit;
                    self.initTypeahead(self.addressEngine);
                }
            });
        }
    }

    $(document).ready(function() {
        $("[data-cmp-is='adaptiveFormAddressInput']").each(function() {
            let $element = $(this);
            let formContainer = $element.closest('.panel').get(0) || $element.closest('.rootPanel').get(0) || null;
            new AddressInput({ element: $element.get(0), formContainer: formContainer });
        });
    });
})();
