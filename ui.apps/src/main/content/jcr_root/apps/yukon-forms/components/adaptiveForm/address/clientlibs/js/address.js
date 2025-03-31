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
            this.fixedLimit = $(this.element).data("limit");
            this.limit = this.fixedLimit;
            this.language = "en";
            this.isFrench = this.language.includes("fr");

            if (this.isFrench) {
                this.language = "fr";
            }
            this.usingRefinedSuggestions = false;

            // setting initial typeahead objects
            this.addressEngine = new Bloodhound({
                remote: {
                    url: this.getUrl({container: '', text: '%QUERY'}),
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
            let url = this.host + "/Capture/Interactive/Find/v1.00/json3ex.ws?";
                url += "Key=" + this.key;
                url += "&Container=" + params.container || '';
                url += "&Origin=CAN";
                url += "&Countries=";
                url += "&Datasets=";
                url += "&Limit=" + this.limit;
                url += "&Filter=";
                url += "&Language=" + this.language;
                url += "&$block=true";
                url += "&$cache=true";
                url += "&Text=" + params.text || "%QUERY";
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
                            if (data && data.Text) {
                                return '<div><span>' + data.Text + ' ' + data.Description + '</span></div>';
                            } else {
                                return '';
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

                // when user selects on dropdown with multiple address
                if (suggestion.Type === "BuildingNumber") {
                    let url = self.getUrl({container: suggestion.Id, text: suggestion.Text})
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
                        url: self.host + '/Capture/Interactive/Retrieve/v1.00/json3ex.ws',
                        data: {
                            Key: self.key,
                            Id: suggestion.Id,
                            $cache: true
                        },
                        dataType: 'json',
                        success: function(response) {
                            var address = response.Items || [];
                            address = !self.isFrench ? address[0] : address[1];
                            self.limit = self.fixedLimit;
                            $(self.container).find('[data-name="address2"]').val(address.Line2).trigger('change');
                            $(self.container).find('[data-name="city"]').val(address.City).trigger('change');
                            $(self.container).find('[data-name="province"]').val(address.ProvinceCode).trigger('change');
                            $(self.container).find('[data-name="postalCode"]').val(address.PostalCode).trigger('change');
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
