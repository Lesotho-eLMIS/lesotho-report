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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.openlmis.report.domain.JasperTemplate;
import org.openlmis.report.exception.JasperReportViewException;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
@PrepareForTest({JasperReportsViewService.class, JasperFillManager.class, DataSource.class,
    ValidatingObjectInputStream.class})
@SuppressWarnings("PMD.TooManyMethods")
public class JasperReportsViewServiceTest {

  private static final String FORMAT_PARAM = "format";
  private static final String PARAM_DATASOURCE = "datasource";
  private static final String FORMAT_CSV = "csv";
  private static final String FORMAT_PDF = "pdf";
  private static final String FORMAT_XLS = "xls";
  private static final String FORMAT_HTML = "html";
  private static final String FORMAT_XLSX = "xlsx";

  @Mock
  private JasperCsvExporter jasperCsvExporter;

  @Mock
  private JasperXlsExporter jasperXlsExporter;

  @Mock
  private JasperXlsxExporter jasperXlsxExporter;

  @Mock
  private JasperHtmlExporter jasperHtmlExporter;

  @Mock
  private JasperPdfExporter jasperPdfExporter;

  @Mock
  private JasperReport jasperReport;

  @Mock
  private ValidatingObjectInputStream validatingObjectInputStream;

  @Mock
  private DataSource replicationDataSource;

  @Spy
  private final JasperTemplate jasperTemplate = new JasperTemplate();

  @Spy
  @InjectMocks
  private final JasperReportsViewService viewService = new JasperReportsViewService();

  @Before
  public void init() throws Exception {
    initializeExporterMocks();

    doReturn(new byte[0]).when(jasperTemplate).getData();

    whenNew(ValidatingObjectInputStream.class).withAnyArguments()
        .thenReturn(validatingObjectInputStream);
    when(validatingObjectInputStream.readObject()).thenReturn(jasperReport);

    mockStatic(JasperFillManager.class);
    when(JasperFillManager.fillReport(any(JasperReport.class), any(), any(Connection.class)))
        .thenReturn(new JasperPrint());
  }

  @Test
  public void shouldSelectCsvExporterForCsvFormat() throws Exception {
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_CSV));
    verify(jasperCsvExporter, times(1)).exportReport();
  }

  @Test
  public void shouldSelectPdfExporterForPdfFormat() throws Exception {
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_PDF));
    verify(jasperPdfExporter, times(1)).exportReport();
  }

  @Test
  public void shouldSelectXlsExporterForXlsFormat() throws Exception {
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_XLS));
    verify(jasperXlsExporter, times(1)).exportReport();
  }

  @Test
  public void shouldSelectHtmlExporterForHtmlFormat() throws Exception {
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_HTML));
    verify(jasperHtmlExporter, times(1)).exportReport();
  }

  @Test
  public void shouldSelectXlsxExporterForXlsxFormat() throws Exception {
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_XLSX));
    verify(jasperXlsxExporter, times(1)).exportReport();
  }

  @Test(expected = JasperReportViewException.class)
  public void shouldThrowJasperReportViewExceptionWhenConnectionCantBeOpen() throws Exception {
    when(replicationDataSource.getConnection()).thenThrow(new SQLException());
    viewService.getJasperReportsView(jasperTemplate, getParamsWithFormat(FORMAT_PDF));
  }

  @Test
  public void shouldUseJrDataSourceWhenProvided() throws Exception {
    Map<String, Object> params = getParamsWithFormat(FORMAT_PDF);
    JRDataSource mockDataSource = mock(JRDataSource.class);
    params.put(PARAM_DATASOURCE, mockDataSource);

    mockStatic(JasperFillManager.class);
    when(JasperFillManager.fillReport(any(JasperReport.class), any(), eq(mockDataSource)))
        .thenReturn(new JasperPrint());

    viewService.getJasperReportsView(jasperTemplate, params);

    verify(jasperPdfExporter, times(1)).exportReport();
  }

  @Test
  public void shouldUseCollectionAsDataSourceWhenProvided() throws Exception {
    Map<String, Object> params = getParamsWithFormat(FORMAT_PDF);
    List<String> collection = new ArrayList<>();
    params.put(PARAM_DATASOURCE, collection);

    mockStatic(JasperFillManager.class);
    when(JasperFillManager.fillReport(any(JasperReport.class), any(),
        any(JRBeanCollectionDataSource.class)))
        .thenReturn(new JasperPrint());

    viewService.getJasperReportsView(jasperTemplate, params);

    verify(jasperPdfExporter, times(1)).exportReport();
  }

  @Test(expected = JasperReportViewException.class)
  public void shouldThrowExceptionWhenDataSourceIsInvalidType() throws Exception {
    Map<String, Object> params = getParamsWithFormat(FORMAT_PDF);
    params.put(PARAM_DATASOURCE, "invalid string datasource");

    viewService.getJasperReportsView(jasperTemplate, params);
  }

  private Map<String, Object> getParamsWithFormat(String format) {
    Map<String, Object> params = new HashMap<>();
    params.put(FORMAT_PARAM, format);
    return params;
  }

  private void initializeExporterMocks() throws Exception {
    whenNew(JasperCsvExporter.class).withAnyArguments().thenReturn(jasperCsvExporter);
    whenNew(JasperXlsExporter.class).withAnyArguments().thenReturn(jasperXlsExporter);
    whenNew(JasperHtmlExporter.class).withAnyArguments().thenReturn(jasperHtmlExporter);
    whenNew(JasperPdfExporter.class).withAnyArguments().thenReturn(jasperPdfExporter);
    whenNew(JasperXlsxExporter.class).withAnyArguments().thenReturn(jasperXlsxExporter);
  }
}
