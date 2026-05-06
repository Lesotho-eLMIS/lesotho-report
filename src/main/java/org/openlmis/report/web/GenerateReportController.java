/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.report.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.openlmis.report.dto.external.GenerateReportDto;
import org.openlmis.report.exception.JasperReportViewException;
import org.openlmis.report.exception.ReportingException;
import org.openlmis.report.service.JasperReportsViewService;
import org.openlmis.report.service.JasperTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Transactional(readOnly = true)
@RequestMapping("/api/reports/generate")
@RequiredArgsConstructor
public class GenerateReportController extends BaseController {
  private static final Logger LOGGER = LoggerFactory.getLogger(GenerateReportController.class);
  private static final String PARAM_FORMAT = "format";
  private static final String PARAM_DATASOURCE = "datasource";
  private static final String PARAM_SUBREPORT_BYTES = "subreport_bytes";

  private final JasperTemplateService jasperTemplateService;
  private final JasperReportsViewService jasperReportsViewService;

  @Value("${dateTimeFormat}")
  private String dateTimeFormat;

  @Value("${dateFormat}")
  private String dateFormat;

  @Value("${groupingSeparator}")
  private String groupingSeparator;

  @Value("${groupingSize}")
  private String groupingSize;

  @Value("${time.zoneId}")
  private String timeZoneId;

  /**
   * Generate report response entity.
   *
   * @param request the request
   * @return the response entity
   * @throws ReportingException        the reporting exception
   * @throws JasperReportViewException the jasper report view exception
   */
  @PostMapping
  public ResponseEntity<byte[]> generateReport(@RequestBody GenerateReportDto request)
      throws ReportingException, JasperReportViewException {
    String templateName = request.getName();
    Map<String, Object> params = new HashMap<>(request.getParams());

    JasperReport templateReport = jasperTemplateService.loadReport(request.getTemplate());

    processDataSource(params);
    processSubreports(params);
    addTranslationsAndHeaders(templateReport, params, templateName);
    addFormattingParameters(params);

    byte[] reportData = jasperReportsViewService.getJasperReportsView(request.getTemplate(),
        params);

    String format = (params.containsKey(PARAM_FORMAT) && params.get(PARAM_FORMAT) != null)
        ? String.valueOf(params.get(PARAM_FORMAT)) : "pdf";
    MediaType mediaType = determineMediaType(format);
    String fileName = templateName.replaceAll("\\s+", "_");

    return ResponseEntity
        .ok()
        .contentType(mediaType)
        .header("Content-Disposition", "inline; filename=" + fileName + "." + format)
        .body(reportData);
  }

  private void processDataSource(Map<String, Object> params) {
    if (params.containsKey(PARAM_DATASOURCE)) {
      Object rawDataSource = params.get(PARAM_DATASOURCE);
      if (rawDataSource instanceof Collection) {
        JRMapCollectionDataSource dataSource =
            new JRMapCollectionDataSource((Collection<Map<String, ?>>) rawDataSource);
        params.put(PARAM_DATASOURCE, dataSource);
      }
    }
  }

  private void processSubreports(Map<String, Object> params) throws ReportingException {
    if (params.containsKey(PARAM_SUBREPORT_BYTES)) {
      String base64String = (String) params.get(PARAM_SUBREPORT_BYTES);
      byte[] subreportData = java.util.Base64.getDecoder().decode(base64String);

      JasperReport compiledSubreport = jasperTemplateService.loadReport(subreportData);
      params.put("subreport", compiledSubreport);
      params.remove(PARAM_SUBREPORT_BYTES);
    }
  }

  private void addTranslationsAndHeaders(JasperReport templateReport, Map<String, Object> params,
                                         String templateName) {
    try {
      String lang = params.containsKey("lang") ? String.valueOf(params.get("lang")) : null;
      params.putAll(jasperTemplateService.getLocaleBundleParameters(lang));
      params.putAll(jasperTemplateService.getMapSubreportGlobalHeaderParameters(templateReport));
    } catch (MalformedURLException e) {
      LOGGER.debug("Cannot load translation bundle for {}", templateName);
    } catch (JRException | IOException ex) {
      LOGGER.debug("Cannot load GlobalHeaderTemplate for {}", templateName);
    }
  }

  private void addFormattingParameters(Map<String, Object> params) {
    params.putIfAbsent(PARAM_FORMAT, "pdf");
    params.putIfAbsent("dateTimeFormat", dateTimeFormat);
    params.putIfAbsent("dateFormat", dateFormat);
    params.putIfAbsent("timeZoneId", timeZoneId);

    DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols();
    decimalFormatSymbols.setGroupingSeparator(groupingSeparator.charAt(0));
    DecimalFormat decimalFormat = new DecimalFormat("", decimalFormatSymbols);
    decimalFormat.setGroupingSize(Integer.parseInt(groupingSize));

    params.put("decimalFormat", decimalFormat);
  }

  private MediaType determineMediaType(String format) {
    if ("csv".equals(format)) {
      return new MediaType("text", "csv", StandardCharsets.UTF_8);
    } else if ("xls".equals(format) || "xlsx".equals(format)) {
      return new MediaType("application",
          "vnd.openxmlformats-officedocument.spreadsheetml.sheet", StandardCharsets.UTF_8);
    } else if ("html".equals(format)) {
      return MediaType.TEXT_HTML;
    }
    return MediaType.APPLICATION_PDF;
  }
}
