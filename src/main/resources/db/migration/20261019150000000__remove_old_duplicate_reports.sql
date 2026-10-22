-- Lesotho: removes old copies of reports that are now kept as .jrxml files in
-- src/main/resources/reports/managed/ and loaded by ManagedReportLoader at start-up:
--   * test uploads on dev ('Data verificationn...', 'Emergecy Orderss')
--   * the old lower-case copies on production ('emergency orders', 'stockout',
--     'facility system usage'), replaced by 'Emergency Orders', 'Stockout', 'Facility System Usage'.
-- Names match exactly (case-sensitive), so the managed reports themselves are never touched.
-- Where a row does not exist this does nothing.

DELETE FROM report.jasper_template_parameter_dependencies WHERE parameterid IN (SELECT id FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage')));
DELETE FROM report.jaspertemplateparameter_options WHERE jaspertemplateparameterid IN (SELECT id FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage')));
DELETE FROM report.template_parameters WHERE templateid IN (SELECT id FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage'));
DELETE FROM report.jasper_templates_report_images WHERE jaspertemplateid IN (SELECT id FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage'));
DELETE FROM report.jaspertemplate_requiredrights WHERE jaspertemplateid IN (SELECT id FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage'));
DELETE FROM report.jasper_templates WHERE name IN ('Data verificationn', 'Data verificationn1', 'Data verificationn2', 'Data verificationn5', 'Data verificationn6', 'Data verificationn7', 'Emergecy Orderss', 'emergency orders', 'stockout', 'facility system usage');
