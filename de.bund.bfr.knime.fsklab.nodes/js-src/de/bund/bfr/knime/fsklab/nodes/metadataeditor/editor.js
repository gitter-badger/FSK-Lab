metadata_editor = function () {

    var softwareDic = {'R': 'R', 'Matlab': 'Matlab'};
    var modelTypeDic = {
        'EXPERIMENTAL_DATA': 'Experimental data',
        'PRIMARY_MODEL_WDATA': 'Primary model with data',
        'PRIMARY_MODEL_WODATA': 'Primary model without data',
        'TWO_STEP_SECONDARY_MODEL': 'Two step secondary model',
        'ONE_STEP_SECONDARY_MODEL': 'One step secondary model',
        'MANUAL_SECONDARY_MODEL': 'Manual secondary model',
        'TWO_STEP_TERTIARY_MODEL': 'Two step tertiary model',
        'ONE_STEP_TERTIARY_MODEL': 'One step tertiary model',
        'MANUAL_TERTIARY_MODEL': 'Manual tertiary model'
    };
    var modelClassDic = {
        'UNKNOWN': 'unknown',
        'GROWTH': 'growth',
        'INACTIVATION': 'inactivation',
        'SURVIVAL': 'survival',
        'GROWTH_INACTIVATION': 'growth/inactivation',
        'INACTIVATION_SURVIVAL': 'inactivation/survival',
        'GROWTH_SURVIVAL': 'growth/survival',
        'GROWTH_INACTIVATION_SURVIVAL': 'growth/inactivation/survival',
        'T': 'T',
        'PH': 'pH',
        'AW': 'aw',
        'T_PH': 'T/pH',
        'T_AW': 'T/aw',
        'PH_AW': 'pH/aw',
        'T_PH_AW': 'T/pH/aw'
    };
    var dataTypeDic = {
        'character': 'character',
        'integer': 'integer',
        'numeric': 'numeric',
        'array': 'array'
    };

    /**
     * Create a form-group envolving an input.
     * - id: Input id
     * - type: Input type {text, url, checkbox}
     * - label: Text label
     * - value: String value
     */
    function InputForm(id, type, label, value) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.value = value;

        this.createHtml = function() {
            var formStr = '<div class="form-group">';
            formStr += '<label for="' + this.id + '" class="col-sm-3 control-label">' + this.label + '</label>';
            formStr += '<div class="col-sm-9">';
            if (this.type === 'text') {
                formStr += '<input type="text" class="form-control no-border" id="' + this.id + '" value="">';
            } else if (this.type === 'url') {
                formStr += '<input type="url" class="form-control no-border" id="' + this.id + '" value="">';
            } else if (this.type === 'checkbox') {
                formStr += '<input id="' + id + '" type="checkbox">';
            }
            formStr += '</div>';
            formStr += '</div>';

            return formStr;
        };

        this.loadData = function() {
            if (this.type === 'text' || this.type === 'url') {
                $('#' + id).val(this.value === null ? "" : this.value);
            } else if (this.type === 'checkbox') {
                $('#' + id).prop('checked', _value.metadata.hasData);
            }
        };

        this.saveData = function() {
            var outer = this;
            if (this.type === 'text' || this.type === 'url') {
                $('#' + this.id).on('input', function() { outer.value = $(this).val(); });
            } else if (this.type === 'checkbox') {
                $('#' + this.id).change(function() { outer.value = $(this).is(':checked'); });
            }
        };

        return this;
    }

    /**
     * Create a table row with variable data.
     * The value property is undefined or null for dependent variable.
     */
    function VariableRow(variable) {
        this.variable = variable;
        this.typeInput = new SelectInput(variable.name + "_type", this.variable.type, dataTypeDic);

        this.createHtml = function() {
            function createTd(val, type) {
                var td = '<td class="has-success">';
                if (type === 'text') {
                    td += '<input type="text" class="form-control input-sm" value="' + val + '">';
                } else if (type === 'checkbox') {
                    td += '<input type="checkbox" class="form-control input-sm" ' + (val ? 'checked' : '') + '>';
                }
                td += '</td>';

                return td;
            }
            
            var row = '<tr>';
            row += createTd(this.variable.name === null ? "" : this.variable.name, 'text');  // name td
            row += createTd(this.variable.unit === null ? "" : this.variable.unit, 'text');  // unit td
            row += '<td>' + this.typeInput.createHtml() + '</td>';  // type td
            row += createTd(this.variable.value === null ? "" : this.variable.value, 'text'); // value td
            row += createTd(this.variable.min === null ? "" : this.variable.min, 'text');  // min td
            row += createTd(this.variable.max === null ? "" : this.variable.max, 'text');  // max td
            row += createTd((this.variable.value ? false : true), 'checkbox');  // dependent td
            row += '<td><button type="button" class="btn btn-default">' +
                   '  <span class="glyphicon glyphicon-minus"></span>' +
                   '</button></td>';
            row += '</tr>';

            return row;
        };

        this.loadData = function() {
            var row = $('tr:first-child:contains(' + this.variable.name + ')').parent();
            $('td:eq(0)', row).text(this.variable.name === null ? "" : this.variable.name);
            $('td:eq(1)', row).text(this.variable.unit === null ? "" : this.variable.unit);
            this.typeInput.loadData();

            var valueInput = $('td:eq(3) input');
            var minInput = $('td:eq(4) input');
            var maxInput = $('td:eq(5) input');

            valueInput.val(this.variable.value);
            minInput.val(this.variable.min);
            maxInput.val(this.variable.max);

            // Disable value, min and max inputs for array variables
            if (this.variable.type === 'array') {
                valueInput.prop('disabled', true);
                minInput.prop('disabled', true);
                maxInput.prop('disabled', true);
            }
        };

        this.saveData = function() {
            var row = $('tr:first-child:contains(' + this.variable.name + ')').parent();

            var outer = this;
            $('td:eq(0)', row).on('input', function() { outer.variable.name = $(this).val(); });
            $('td:eq(1)', row).on('input', function() { outer.variable.unit = $(this).val(); });
            this.typeInput.saveData();

            var valueInput = $('td:eq(3) input', row);
            var minInput = $('td:eq(4) input', row);
            var maxInput = $('td:eq(5) input', row);

            // Independent variable
            if (this.value) {
                valueInput.on('input', function() {
                    var newVal = Number(valueInput.val());
                    if (this.variable.type === 'integer' && newVal % 2 !== 0) {
                        _markInvalidTd(valueInput.parent());
                    } else {
                        var min = Number(this.variable.min);
                        var max = Number(this.variable.max);
                        if (min <= newVal && newVal <= max) {
                            this.variable.value = newVal;
                            _markValidTd(valueInput.parent());
                        } else {
                            _markInvalidTd(valueInput.parent());
                        }
                    }
                });
                minInput.on('input', function() {
                    var newVal = Number(minInput.val());
                    var max = Number(this.variable.max);
                    if (newVal < max) {
                        this.variable.min = newVal;
                        _markValidTd(minInput.parent());
                    } else {
                        _markInvalidTd(minInput.parent());
                    }
                });
                maxInput.on('input', function() {
                    var newVal = Number(maxInput.val());
                    var min = Number(this.variable.min);
                    if (newVal > min) {
                        this.variable.max = newVal;
                        _markValidTd(maxInput.parent());
                    } else {
                        _markInvalidTd(maxInput.parent());
                    }
                });
            }
            // Dependent variable
            else {
                minInput.on('input', function() {
                    var newVal = Number(minInput.val());
                    var max = Number(this.variable.max);

                    if (newVal < max) {
                        this.variable.min = newVal;
                        _markValidTd(minInput.parent());
                    } else {
                        _markInvalidTd(minInput.parent());
                    }
                });
                maxInput.on('input', function() {
                    var newVal = Number(maxInput.val());
                    var min = Number(this.variable.min);
                    if (newVal > min) {
                        this.variable.max = newVal;
                        _markValidTd(maxInput.parent());
                    } else {
                        _markInvalidTd(maxInput.parent());
                    }
                });
            }


        };

        /** Mark a table cell as valid. */
        function _markValidTd(td) {
            td.removeClass('has-error');
            td.addClass('has-success');
        }

        /** Mark a table cell as invalid. */
        function _markInvalidTd(td) {
            td.removeClass('has-success');
            td.addClass('has-error');
        }

        return this;
    }

    /**
     * Create a Bootstrap select input.
     * - id: Select id
     * - value: String value with the selected option
     * - entries: Dictionary with labels as keys and option values as values
     */
    function SelectInput(id, value, entries) {
        this.id = id;
        this.value = value;
        this.entries = entries;

        this.createHtml = function() {
            var html = '<select class="form-control no-border" id="' + this.id + '">';
            for (var key in this.entries) {
                html += '<option value="' + key + '">' + entries[key] + '</option>';
            }
            html += '</select>';

            return html;
        };

        this.loadData = function() {
            if (this.value) {
                var option = $('#' + this.id + ' option[value="' + this.value + '"]');
                option.prop('selected', true);
            }
        };

        this.saveData = function() {
            var outer = this;
            $('#' + this.id).change(function() { outer.value = $(this).val(); });
        };

        return this;
    }

    /**
     * Create a form-group envolving a textarea.
     * - id: Textarea id
     * - label: Text label
     * - value: String value
     */
    function TextAreaForm(id, label, value) {
        this.id = id;
        this.label = label;
        this.value = value;

        this.createHtml = function() {
            return '<div class="form-group">' +
                '  <label for="' + this.id + '" class="col-sm-3 control-label">' + this.label + '</label>' +
                '  <div class="col-sm-9">' +
                '    <textarea id="' + this.id + '" class="form-control no-border" rows="3"></textArea>' +
                '  </div>' +
                '</div>';
        };

        this.loadData = function() {
            $('#' + this.id).val(this.value === null ? "" : this.value);
        };

        this.saveData = function() {
            var outer = this;
            $('#' + this.id).on('input', function() { outer.value = $(this).val(); });
        };

        return this;
    }

    /**
     * Create a form-group envolving a select.
     * - id: Select id
     * - label: Text label
     * - entries: Dictionary where the keys are the labels and the values are the option values
     * - value: String value with the selected option
     */
    function SelectForm(id, label, entries, value) {
        this.id = id;
        this.label = label;
        this.entries = entries;
        this.value = value;

        this.createHtml = function() {
            var formStr = '<div class="form-group">';
            formStr += '<label for="' + this.id + '" class="col-sm-3 control-label">' + this.label + '</label>';
            formStr += '<div class="col-sm-9">';
            formStr += '<select class="form-control no-border" id="' + this.id + '">';
            for (var key in this.entries) {
                formStr += '<option value="' + key + '">' + entries[key] + '</option>';
            }
            formStr += '</select></div></div>';

            return formStr;
        };

        this.loadData = function() {
            if (this.value) {
                var option = $('#' + this.id + ' option[value="' + this.value + '"]');
                option.prop('selected', true);
            }
        };

        this.saveData = function() {
            var outer = this;
            $('#' + this.id).change(function() { outer.value = $(this).val(); });
        };

        return this;
    }

    var editor = {
	   version: "0.0.1"
    };
    editor.name = "FSK Metadata Editor";

    var _value;  // Raw FskMetadataEditorViewValue

    var _modelNameInput = new InputForm("modelNameInput", "text", "Model name", "");
    var _modelIdInput = new InputForm("modelIdInput", "text", "Model id", "");
    var _modelLinkInput = new InputForm("modelLinkInput", "url", "Model link", "");
    var _organismInput = new InputForm("organismInput", "text", "Organism", "");
    var _organismDetailsInput = new InputForm("organismDetailsInput", "text", "Organism details", "");
    var _matrixInput = new InputForm("matrixInput", "text", "Matrix", "");
    var _matrixDetailsInput = new InputForm("matrixDetailsInput", "text", "Matrix details", "");
    var _creatorInput = new InputForm("creatorInput", "text", "Creator", "");
    var _familyNameInput = new InputForm("familyNameInput", "text", "Family name", "");
    var _contactInput = new InputForm("contactInput", "text", "Contact", "");
    var _softwareInput = new SelectForm("softwareInput", "Software", softwareDic, "");
    var _referenceDescriptionInput = new InputForm("referenceDescriptionInput", "text", "Reference description", "");
    var _referenceDescriptionLinkInput = new InputForm("referenceDescriptionLinkInput", "url", "Reference description link", "");
    var _createdDateInput = new InputForm("createdDateInput", "text", "Created date", "");
    var _modifiedDateInput = new InputForm("modifiedDateInput", "text", "Modified date", "");
    var _rightsInput = new InputForm("rightsInput", "text", "Rights", "");
    var _notesInput = new TextAreaForm("notesInput", "Notes:", "");
    var _curatedInput = new InputForm("curatedInput", "checkbox", "Curated", "");
    var _typeInput = new SelectForm("typeInput", "Model type", modelTypeDic, "");
    var _subjectInput = new SelectForm("subjectInput", "Model subject", modelClassDic, "");
    var _foodProcessInput = new InputForm("foodProcessInput", "text", "Food process", "");
    var _hasDataInput = new InputForm("hasDataInput", "checkbox", "Has data?:", "");

    var _variableRows;

    editor.init = function (representation, value)
    {
        _value = value;

        // Initialize input with input metadata
        _modelNameInput.value = _value.metadata.modelName;
        _modelIdInput.value = _value.metadata.modelId;
        _modelLinkInput.value = _value.metadata.modelLink;
        _organismInput.value = _value.metadata.organism;
        _organismDetailsInput.value = _value.metadata.organismDetails;
        _matrixInput.value = _value.metadata.matrix;
        _matrixDetailsInput.value = _value.metadata.matrixDetails;
        _creatorInput.value = _value.metadata.creator;
        _familyNameInput.value = _value.metadata.familyName;
        _contactInput.value = _value.metadata.contact;
        _softwareInput.value = _value.metadata.software;
        _referenceDescriptionInput.value = _value.metadata.referenceDescription;
        _referenceDescriptionLinkInput.value = _value.metadata.referenceDescriptionLink;
        _createdDateInput.value = _value.metadata.createdDate;
        _modifiedDateInput.value = _value.metadata.modifiedDate;
        _rightsInput.value = _value.metadata.rights;
        _notesInput.value = _value.metadata.notes;
        _curatedInput.value = _value.metadata.curated;
        _typeInput.value = _value.metadata.type;
        _subjectInput.value = _value.metadata.subject;
        _foodProcessInput.value = _value.metadata.foodProcess;
        _hasDataInput.value = _value.metadata.hasData;

        _variableRows = [];
        _variableRows.push(new VariableRow(_value.metadata.dependentVariable));
        for (var i = 0; i < _value.metadata.independentVariables.length; i++) {
            _variableRows.push(new VariableRow(_value.metadata.independentVariables[i]));
        }

        checkVariables();
        create_body ();
    };


    editor.getComponentValue = function ()
    {
        // assign input values to _value
        _value.metadata.modelName = _modelNameInput.value;
        _value.metadata.modelId = _modelIdInput.value;
        _value.metadata.modelLink = _modelLinkInput.value;
        _value.metadata.organism = _organismInput.value;
        _value.metadata.organismDetails = _organismDetailsInput.value;
        _value.metadata.matrix = _matrixInput.value;
        _value.metadata.matrixDetails = _matrixDetailsInput.value;
        _value.metadata.creator = _creatorInput.value;
        _value.metadata.familyName = _familyNameInput.value;
        _value.metadata.contact = _contactInput.value;
        _value.metadata.software = _softwareInput.value;
        _value.metadata.referenceDescription = _referenceDescriptionInput.value;
        _value.metadata.referenceDescriptionLink = _referenceDescriptionLinkInput.value;
        _value.metadata.createdDate = _createdDateInput.value;
        _value.metadata.modifiedDate = _modifiedDateInput.value;
        _value.metadata.rights = _rightsInput.value;
        _value.metadata.notes = _notesInput.value;
        _value.metadata.curated = _curatedInput.value;
        _value.metadata.type = _typeInput.value;
        _value.metadata.subject = _subjectInput.value;
        _value.metadata.foodProcess = _foodProcessInput.value;
        _value.metadata.hasData = _hasDataInput.value;

        return _value;
    };

    return editor;

    // --- utility functions ---
    function create_body ()
    {
        var varTable =
            '<table class="table table-condensed">' +
            '<tr>' +
            '<th>Name</th>' +
            '<th>Unit</th>' +
            '<th>Type</th>' +
            '<th>Value</th>' +
            '<th>Min</th>' +
            '<th>Max</th>' +
            '<th>Dependent</th>' +
            '<th>Remove</th>' +
            '</tr>';
        for (var i = 0; i < _variableRows.length; i++) {
            varTable += _variableRows[i].createHtml();
        }
        varTable += '</table>';

        var form = '<form class="form-horizontal">';
        form += _modelNameInput.createHtml();
        form += _modelIdInput.createHtml();
        form += _modelLinkInput.createHtml();
        form += _organismInput.createHtml();
        form += _organismDetailsInput.createHtml();
        form += _matrixInput.createHtml();
        form += _matrixDetailsInput.createHtml();
        form += _creatorInput.createHtml();
        form += _familyNameInput.createHtml();
        form += _contactInput.createHtml();
        form += _softwareInput.createHtml();
        form += _referenceDescriptionInput.createHtml();
        form += _referenceDescriptionLinkInput.createHtml();
        form += _createdDateInput.createHtml();
        form += _modifiedDateInput.createHtml();
        form += _rightsInput.createHtml();
        form += _notesInput.createHtml();
        form += _curatedInput.createHtml();
        form += _typeInput.createHtml();
        form += _subjectInput.createHtml();
        form += _foodProcessInput.createHtml();
        form += _hasDataInput.createHtml();
        form += '</form>';

        document.createElement("body");
        $("body").html('<div class="container">' + form + varTable + '</div');

        // Create date pickers. Set date formats and save when dates change.
        $("#createdDateInput").datepicker({
            dateFormat: "mm.dd.yy",
            onSelect: function(dateText) { _createdDateInput.value = dateText; }
        });
        $("#modifiedDateInput").datepicker({
            dateFormat: "mm.dd.yy",
            onSelect: function(dateText) { _modifiedDateInput.value = dateText; }
        });

        loadData();
        saveData();
    }

    function loadData () {
        _modelNameInput.loadData();
        _modelIdInput.loadData();
        _modelLinkInput.loadData();
        _organismInput.loadData();
        _organismDetailsInput.loadData();
        _matrixInput.loadData();
        _matrixDetailsInput.loadData();
        _creatorInput.loadData();
        _familyNameInput.loadData();
        _contactInput.loadData();
        _softwareInput.loadData();
        _referenceDescriptionInput.loadData();
        _referenceDescriptionLinkInput.loadData();
        _createdDateInput.loadData();
        _modifiedDateInput.loadData();
        _rightsInput.loadData();
        _notesInput.loadData();
        _curatedInput.loadData();
        _typeInput.loadData();
        _subjectInput.loadData();
        _foodProcessInput.loadData();
        _hasDataInput.loadData();
        for (var i = 0; i < _variableRows.length; i++) {
            _variableRows[i].loadData();
        }
    }

    /**
     * Saves data on modification.
     * 
     * - Text inputs use the oninput event.
     * - Selects and checkboxes use the onchange event.
     * - jQuery datepickers are already saving their data. No need to save it here.
     */
    function saveData () {
        _modelNameInput.saveData();
        _modelIdInput.saveData();
        _modelLinkInput.saveData();
        _organismInput.saveData();
        _organismDetailsInput.saveData();
        _matrixInput.saveData();
        _matrixDetailsInput.saveData();
        _creatorInput.saveData();
        _familyNameInput.saveData();
        _contactInput.saveData();
        _softwareInput.saveData();
        _referenceDescriptionInput.saveData();
        _referenceDescriptionLinkInput.saveData();
        _createdDateInput.saveData();
        _modifiedDateInput.saveData();
        _rightsInput.saveData();
        _notesInput.saveData();
        _curatedInput.saveData();
        _typeInput.saveData();
        _subjectInput.saveData();
        _foodProcessInput.saveData();
        _hasDataInput.saveData();

        for (var i = 0; i < _variableRows.length; i++) {
            _variableRows[i].saveData();
        }

        /** Mark a table cell as valid. */
        function markValidTd(td) {
            td.removeClass('has-error');
            td.addClass('has-success');
        }

        /** Mark a table cell as invalid. */
        function markInvalidTd(td) {
            td.removeClass('has-success');
            td.addClass('has-error');
        }
    }

    /** Check variables. Integer variables will truncate decimals. */
    function checkVariables() {
        for (var i = 0; i < _value.metadata.independentVariables.length; i++) {
            var variable = _value.metadata.independentVariables[i];
            if (variable.type === 'integer') {
                variable.value = Math.floor(variable.value);
            }
        }
    }
}();