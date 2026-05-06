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

package org.openlmis.report.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openlmis.report.domain.JasperTemplate;
import org.openlmis.report.exception.JasperReportViewException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@DirtiesContext
@Transactional
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class JasperReportsViewServiceIntegrationTest {

  private static final String FORMAT_PARAM = "format";
  private static final String EMPTY_REPORT_RESOURCE = "/empty-report.jrxml";
  private static final int HIKARI_DEFAULT_POOL_SIZE = 10;

  private JasperTemplate template;

  @Autowired
  private JasperReportsViewService service;

  @Before
  public void init() throws JRException, IOException {
    template = prepareSampleTemplate();
  }

  @Test
  public void generateReportShouldNotThrowErrorAfterPrintingReport10Times()
      throws JasperReportViewException {
    for (int i = 0; i < HIKARI_DEFAULT_POOL_SIZE + 1; i++) {
      service.getJasperReportsView(template, getParamsWithFormat("pdf"));
    }
  }

  @Test
  public void generateReportShouldNotThrowErrorForAnyOfTheSupportedFormats()
      throws JasperReportViewException {
    service.getJasperReportsView(template, getParamsWithFormat("pdf"));
    service.getJasperReportsView(template, getParamsWithFormat("csv"));
    service.getJasperReportsView(template, getParamsWithFormat("xls"));
    service.getJasperReportsView(template, getParamsWithFormat("html"));
    service.getJasperReportsView(template, getParamsWithFormat("xlsx"));
  }

  @Test(expected = JasperReportViewException.class)
  public void shouldThrowJasperReportViewExceptionInsteadOfNullPointerException()
      throws JasperReportViewException {
    service.getJasperReportsView((byte[]) null, null);
  }

  @Test (expected = JasperReportViewException.class)
  public void generateReportShouldThrowErrorForUnsupportedFormat()
      throws JasperReportViewException {
    service.getJasperReportsView(template, getParamsWithFormat("txt"));
  }

  private JasperTemplate prepareSampleTemplate() throws JRException, IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bos);
    out.writeObject(getEmptyReport());
    out.flush();

    JasperTemplate result = new JasperTemplate();
    result.setData(bos.toByteArray());
    return result;
  }

  private Map<String, Object> getParamsWithFormat(String format) {
    Map<String, Object> params = new HashMap<>();
    params.put(FORMAT_PARAM, format);
    return params;
  }

  private JasperReport getEmptyReport() throws JRException {
    return JasperCompileManager
        .compileReport(getClass().getResourceAsStream(EMPTY_REPORT_RESOURCE));
  }
}
