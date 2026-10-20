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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.apache.commons.lang3.StringUtils;
import org.openlmis.report.domain.JasperTemplate;
import org.openlmis.report.domain.JasperTemplateParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lesotho: keeps the reports in the database in step with the .jrxml files in the repo.
 *
 * <p>When the service starts, every file in {@code reports/managed/} is compiled and saved
 * through {@link JasperTemplateService#saveTemplate} - exactly what an upload through
 * POST /api/reports/templates/common does - so a report is created when it is new and
 * updated in place (same id) when it already exists. Editing a .jrxml and deploying is all
 * it takes; no upload and no migration is needed.</p>
 *
 * <p>Each file names its report with properties inside the .jrxml:</p>
 * <pre>
 *   &lt;property name="lesotho.report.name" value="Stock Status"/&gt;            (required)
 *   &lt;property name="lesotho.report.description" value="..."/&gt;              (optional)
 *   &lt;property name="lesotho.report.category" value="Default Category"/&gt;    (optional)
 * </pre>
 *
 * <p>A parameter can also set the data type the reports page sees, which an upload would
 * otherwise reset to the Java class (e.g. java.lang.String):</p>
 * <pre>
 *   &lt;parameter name="facility" class="java.lang.String"&gt;
 *     &lt;property name="lesotho.dataType" value="MULTISELECT"/&gt;
 * </pre>
 *
 * <p>A file that fails to compile is logged and skipped; the report keeps its last good
 * version and the service still starts.</p>
 */
@Component
public class ManagedReportLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedReportLoader.class);

  static final String NAME_PROPERTY = "lesotho.report.name";
  static final String DESCRIPTION_PROPERTY = "lesotho.report.description";
  static final String CATEGORY_PROPERTY = "lesotho.report.category";
  static final String DEFAULT_CATEGORY = "Default Category";
  static final String DATA_TYPE_PROPERTY = "lesotho.dataType";

  private final JasperTemplateService jasperTemplateService;
  private final TransactionTemplate transactionTemplate;
  private final boolean enabled;
  private final String location;

  /**
   * Creates the loader.
   */
  public ManagedReportLoader(JasperTemplateService jasperTemplateService,
      PlatformTransactionManager transactionManager,
      @Value("${lesotho.reports.managed.enabled:true}") boolean enabled,
      @Value("${lesotho.reports.managed.location:classpath*:reports/managed/*.jrxml}")
          String location) {
    this.jasperTemplateService = jasperTemplateService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.enabled = enabled;
    this.location = location;
  }

  /**
   * Loads every managed report once the application (and its Flyway migrations) is ready.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void loadManagedReports() {
    if (!enabled) {
      LOGGER.info("Managed reports: disabled (lesotho.reports.managed.enabled=false)");
      return;
    }
    Resource[] resources;
    try {
      resources = new PathMatchingResourcePatternResolver().getResources(location);
    } catch (IOException ex) {
      LOGGER.error("Managed reports: cannot read {}", location, ex);
      return;
    }
    Arrays.sort(resources, (a, b) ->
        String.valueOf(a.getFilename()).compareTo(String.valueOf(b.getFilename())));

    List<String> loaded = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (Resource resource : resources) {
      String file = resource.getFilename();
      try {
        String name = load(resource);
        loaded.add(name);
        LOGGER.info("Managed reports: loaded '{}' from {}", name, file);
      } catch (Exception ex) {
        failed.add(file);
        LOGGER.error("Managed reports: FAILED to load {} - the report keeps its previous "
            + "version. Reason: {}", file, ex.getMessage(), ex);
      }
    }
    LOGGER.info("Managed reports: {} loaded, {} failed{}", loaded.size(), failed.size(),
        failed.isEmpty() ? "" : " " + failed);
  }

  String load(Resource resource) throws IOException, JRException {
    byte[] content;
    try (InputStream in = resource.getInputStream()) {
      content = StreamUtils.copyToByteArray(in);
    }
    JasperDesign design = JRXmlLoader.load(new ByteArrayInputStream(content));
    String name = StringUtils.trimToNull(design.getProperty(NAME_PROPERTY));
    if (name == null) {
      throw new IllegalStateException("missing <property name=\"" + NAME_PROPERTY
          + "\" value=\"...\"/> in the .jrxml");
    }
    String description = StringUtils.defaultIfBlank(
        design.getProperty(DESCRIPTION_PROPERTY), name);
    String category = StringUtils.defaultIfBlank(
        design.getProperty(CATEGORY_PROPERTY), DEFAULT_CATEGORY);
    MultipartFile file = new InMemoryJrxml(resource.getFilename(), content);

    transactionTemplate.execute(status -> {
      try {
        // Same call as an upload with override=true: creates, or updates in place by name.
        JasperTemplate template = jasperTemplateService.saveTemplate(
            file, name, description, Collections.emptyList(), category, true);
        applyDataTypes(design, template);
      } catch (Exception ex) {
        status.setRollbackOnly();
        throw new IllegalStateException(ex.getMessage(), ex);
      }
      return null;
    });
    return name;
  }

  /**
   * Applies lesotho.dataType parameter properties; saved when the transaction commits.
   */
  static void applyDataTypes(JasperDesign design, JasperTemplate template) {
    if (template == null || template.getTemplateParameters() == null) {
      return;
    }
    for (JasperTemplateParameter parameter : template.getTemplateParameters()) {
      JRParameter jrParameter = design.getParametersMap().get(parameter.getName());
      if (jrParameter == null) {
        continue;
      }
      String dataType = StringUtils.trimToNull(
          jrParameter.getPropertiesMap().getProperty(DATA_TYPE_PROPERTY));
      if (dataType != null) {
        parameter.setDataType(dataType);
      }
    }
  }

  /**
   * A .jrxml held in memory, handed to the same service method an upload uses.
   */
  static class InMemoryJrxml implements MultipartFile {
    private final String filename;
    private final byte[] content;

    InMemoryJrxml(String filename, byte[] content) {
      this.filename = filename;
      this.content = content;
    }

    @Override
    public String getName() {
      return "file";
    }

    @Override
    public String getOriginalFilename() {
      return filename;
    }

    @Override
    public String getContentType() {
      return "application/xml";
    }

    @Override
    public boolean isEmpty() {
      return content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() {
      return content.clone();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
      Files.write(dest.toPath(), content);
    }
  }

}
