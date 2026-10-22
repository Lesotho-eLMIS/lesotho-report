-- Lesotho: 20260807064200000 inserts 'Commodities Received by Delivery Note' without deleting
-- it first (the other 2026-08-07 reports have a delete migration before their create).
-- Where the report already exists (uploaded by API, or loaded by ManagedReportLoader) that insert
-- fails on the unique name and the service does not start. Remove it here; the create migration
-- adds it back and ManagedReportLoader then updates it to the current .jrxml.
-- Where the row does not exist this does nothing.

DELETE FROM report.jasper_template_parameter_dependencies WHERE parameterid IN (SELECT id FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note'));
DELETE FROM report.jaspertemplateparameter_options WHERE jaspertemplateparameterid IN (SELECT id FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note'));
DELETE FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note');
DELETE FROM report.jasper_templates_report_images WHERE jaspertemplateid IN (SELECT id FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note');
DELETE FROM report.jaspertemplate_requiredrights WHERE jaspertemplateid IN (SELECT id FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note');
DELETE FROM report.jasper_templates WHERE name = 'Commodities Received by Delivery Note';
