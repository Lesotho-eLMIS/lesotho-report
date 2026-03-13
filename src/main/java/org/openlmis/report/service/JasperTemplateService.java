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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openlmis.report.i18n.AuthorizationMessageKeys.ERROR_RIGHT_NOT_FOUND;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_FILE_INVALID;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_IO;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_PARAMETER_INCORRECT_TYPE;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_PARAMETER_MISSING;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_TEMPLATE_EXIST;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.util.JRLoader;

import org.openlmis.report.domain.JasperTemplate;
import org.openlmis.report.domain.JasperTemplateParameter;
import org.openlmis.report.domain.JasperTemplateParameterDependency;
import org.openlmis.report.domain.ReportCategory;
import org.openlmis.report.domain.ReportImage;
import org.openlmis.report.exception.JasperReportViewException;
import org.openlmis.report.exception.ReportingException;
import org.openlmis.report.exception.ValidationMessageException;
import org.openlmis.report.i18n.ReportCategoryMessageKeys;
import org.openlmis.report.i18n.ReportImageMessageKeys;
import org.openlmis.report.repository.JasperTemplateRepository;
import org.openlmis.report.repository.ReportCategoryRepository;
import org.openlmis.report.repository.ReportImageRepository;
import org.openlmis.report.service.referencedata.RightReferenceDataService;
import org.openlmis.report.utils.Message;
import org.openlmis.report.utils.ReportingValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@SuppressWarnings("PMD.TooManyMethods")
public class JasperTemplateService {
  static final String REPORT_TYPE_PROPERTY = "reportType";
  private static final String DEFAULT_REPORT_TYPE = "Consistency Report";
  private static final String[] ALLOWED_FILETYPES = {"jrxml"};
  private static final String CONFIG_PATH = "/config/reports/";

  @Autowired
  private JasperTemplateRepository jasperTemplateRepository;

  @Autowired
  private RightReferenceDataService rightReferenceDataService;

  @Autowired
  private ReportImageRepository reportImageRepository;

  @Autowired
  private ReportCategoryRepository reportCategoryRepository;

  /**
   * Saves a template with given name.
   * If template already exists, only description and required rights are updated.
   *
   * @param file report file
   * @param name name of report
   * @param description report's description
   * @return saved report template
   */
  public JasperTemplate saveTemplate(
      MultipartFile file, String name, String description, List<String> requiredRights,
      String category) throws ReportingException {
    validateRequiredRights(requiredRights);
    JasperTemplate jasperTemplate = jasperTemplateRepository.findByName(name);

    Optional<ReportCategory> reportCategory = reportCategoryRepository.findByName(category);
    if (!reportCategory.isPresent()) {
      throw new ReportingException(
        ReportCategoryMessageKeys.ERROR_REPORT_CATEGORY_NOT_FOUND);
    }

    if (jasperTemplate == null) {
      jasperTemplate = JasperTemplate.builder()
          .name(name)
          .type(DEFAULT_REPORT_TYPE)
          .description(description)
          .requiredRights(requiredRights)
          .category(reportCategory.get())
          .build();
    } else {
      jasperTemplate.setDescription(description);
      jasperTemplate.getRequiredRights().clear();
      jasperTemplate.getRequiredRights().addAll(requiredRights);
      jasperTemplate.setCategory(reportCategory.get());
    }

    validateFileAndSaveTemplate(jasperTemplate, file);
    return jasperTemplate;
  }

  /**
   * Map request parameters to the template parameters in the template. If there are no template
   * parameters, returns an empty Map.
   *
   * @param request  request with parameters
   * @param template template with parameters
   * @return Map of matching parameters, empty Map if none match
   */
  public Map<String, Object> mapRequestParametersToTemplate(
      HttpServletRequest request, JasperTemplate template) {
    List<JasperTemplateParameter> templateParameters = template.getTemplateParameters();
    if (templateParameters == null) {
      return new HashMap<>();
    }

    Map<String, String[]> requestParameterMap = request.getParameterMap();
    Map<String, Object> map = new HashMap<>();

    for (JasperTemplateParameter templateParameter : templateParameters) {
      String templateParameterName = templateParameter.getName();

      for (String requestParamName : requestParameterMap.keySet()) {

        if (templateParameterName.equalsIgnoreCase(requestParamName)) {
          String requestParamValue = "";
          if (requestParameterMap.get(templateParameterName).length > 0) {
            requestParamValue = requestParameterMap.get(templateParameterName)[0];
          }

          if (!(isBlank(requestParamValue)
              || "null".equals(requestParamValue)
              || "undefined".equals(requestParamValue))) {
            map.put(templateParameterName, requestParamValue);
          }
        }
      }
    }

    return map;
  }

  /**
   * Map report images to the template parameters in the template. If there are no template
   * parameters that are associated with images, returns an empty Map.
   *
   * @param template template with parameters
   * @return Map of matching parameters, empty Map if none match
   */
  public Map<String, BufferedImage> mapReportImagesToTemplate(JasperTemplate template)
      throws JasperReportViewException {
    Set<ReportImage> images = template.getReportImages();
    if (images == null) {
      return new HashMap<>();
    }
    Map<String, BufferedImage> map = new HashMap<>();
    for (ReportImage image : images) {
      try {
        InputStream inputStream = new ByteArrayInputStream(image.getData());
        map.put(image.getName(), ImageIO.read(inputStream));
      } catch (IOException ex) {
        throw new JasperReportViewException(ex, ERROR_REPORTING_IO, ex.getMessage());
      }
    }
    return map;
  }

  /**
   * Gets locale for translation resource bundle parameters.
   *
   * @param userLocaleString the user locale string
   * @return the locale bundle parameters
   * @throws MalformedURLException the malformed url exception
   */
  public Map<String, Object> getLocaleBundleParameters(JasperReport parentReport,
                                                       String userLocaleString)
      throws MalformedURLException {
    // validate if report requires resource bundle or not
    String resourceBundleName = parentReport != null ? parentReport.getResourceBundle() : null;
    if (resourceBundleName == null || resourceBundleName.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    Locale userLocale;
    try {
      // try to parse locale param else fallback to english
      userLocale = new Locale.Builder().setLanguageTag(userLocaleString).build();
    } catch (Exception e) {
      userLocale = Locale.ENGLISH;
    }
    Map<String, Object> parameters = new HashMap<>();
    File resourceBundleDir = new File(CONFIG_PATH + "resourceBundles");
    if (resourceBundleDir.exists() && resourceBundleDir.isDirectory()) {
      URL[] urls = {resourceBundleDir.toURI().toURL()};

      try (URLClassLoader externalLoader = new URLClassLoader(urls)) {
        ResourceBundle externalBundle = ResourceBundle
            .getBundle("report_translations", userLocale, externalLoader);

        parameters.put(JRParameter.REPORT_RESOURCE_BUNDLE, externalBundle);
        parameters.put(JRParameter.REPORT_LOCALE, userLocale);
      } catch (IOException | MissingResourceException e) {
        // No translations bundle
        return Collections.emptyMap();
      }
    }
    return parameters;
  }

  /**
   * Gets map subreport global header parameters.
   *
   * @param parentReport the parent report
   * @return the map subreport global header parameters
   * @throws JRException the jr exception
   * @throws IOException the io exception
   */
  public Map<String, Object> getMapSubreportGlobalHeaderParameters(JasperReport parentReport)
      throws JRException, IOException {
    // validate if report requires header or not
    boolean needsHeader = parentReport != null && parentReport.getParameters() != null
        && Arrays.stream(parentReport.getParameters())
        .anyMatch(param -> "headerTemplate".equals(param.getName()));
    if (!needsHeader) {
      return Collections.emptyMap();
    }

    File configDir = new File(CONFIG_PATH);
    if (!configDir.exists() || !configDir.isDirectory()) {
      // config directory does not exist
      return Collections.emptyMap();
    }

    String headerName;
    if (OrientationEnum.LANDSCAPE.equals(parentReport.getOrientationValue())) {
      headerName = "GlobalHeaderLandscape";
    } else if (OrientationEnum.PORTRAIT.equals(parentReport.getOrientationValue())) {
      headerName = "GlobalHeaderPortrait";
    } else {
      // no orientation recognized
      return Collections.emptyMap();
    }

    Map<String, Object> parameters = new HashMap<>();
    File headerFile = new File(CONFIG_PATH + headerName + ".jrxml");
    if (headerFile.exists()) {
      try (InputStream is = Files.newInputStream(headerFile.toPath())) {
        JasperReport globalHeader = JasperCompileManager.compileReport(is);
        parameters.put("headerTemplate", globalHeader);
      }
    } else {
      return Collections.emptyMap();
    }

    parameters.putAll(injectDynamicHeaderParams());
    return parameters;
  }

  /**
   * Inject dynamic header params map.
   *
   * @return the map
   * @throws IOException the io exception
   */
  private Map<String, Object> injectDynamicHeaderParams() throws IOException {
    Map<String, Object> parameters = new HashMap<>();
    File configFile = new File(CONFIG_PATH + "header_config.properties");

    if (configFile.exists()) {
      Properties dynamicProps = new Properties();
      try (InputStream is = Files.newInputStream(configFile.toPath())) {
        dynamicProps.load(is);
      }

      for (String key : dynamicProps.stringPropertyNames()) {
        String value = dynamicProps.getProperty(key);

        if (key.endsWith("Image")) {
          File imageFile = new File(CONFIG_PATH + value);
          if (imageFile.exists()) {
            parameters.put(key, imageFile.getAbsolutePath());
          }
        } else {
          parameters.put(key, value);
        }
      }
    }
    return parameters;
  }

  /**
   * Load report jasper report.
   *
   * @param jasperTemplate the jasper template
   * @return the jasper report
   * @throws ReportingException the reporting exception
   */
  public JasperReport loadReport(JasperTemplate jasperTemplate) throws ReportingException {
    if (jasperTemplate != null) {
      try (InputStream is = new ByteArrayInputStream(jasperTemplate.getData())) {
        return (JasperReport) JRLoader.loadObject(is);
      } catch (JRException ex) {
        throw new ReportingException(ex, ERROR_REPORTING_FILE_INVALID);
      } catch (IOException ex) {
        throw new ReportingException(ex, ERROR_REPORTING_IO, ex.getMessage());
      }
    }
    return null;
  }

  /**
   * Validate ".jrmxl" file and insert this template to database.
   * Throws reporting exception if an error occurs during file validation or parsing,
   */
  void validateFileAndInsertTemplate(JasperTemplate jasperTemplate, MultipartFile file)
      throws ReportingException {
    throwIfTemplateWithSameNameAlreadyExists(jasperTemplate.getName());
    validateFileAndSetData(jasperTemplate, file);
    saveWithParameters(jasperTemplate);
  }

  /**
   * Insert template and template parameters to database.
   */
  void saveWithParameters(JasperTemplate jasperTemplate) {
    jasperTemplateRepository.save(jasperTemplate);
  }

  /**
   * Validate ".jrmxl" file and insert if template not exist. If this name of template already
   * exist, remove older template and insert new.
   */
  void validateFileAndSaveTemplate(JasperTemplate jasperTemplate, MultipartFile file)
      throws ReportingException {
    JasperTemplate templateTmp = jasperTemplateRepository.findByName(jasperTemplate.getName());
    if (templateTmp != null) {
      jasperTemplateRepository.deleteById(templateTmp.getId());
    }
    validateFileAndSetData(jasperTemplate, file);
    saveWithParameters(jasperTemplate);
  }

  /**
   * Validate ".jrxml" report file with JasperCompileManager. If report is valid create additional
   * report parameters. Save additional report parameters as JasperTemplateParameter list. Save
   * report file as ".jasper" in byte array in Template class. If report is not valid throw
   * exception.
   */
  private void validateFileAndSetData(JasperTemplate jasperTemplate, MultipartFile file)
      throws ReportingException {
    ReportingValidationHelper.throwIfFileIsNull(file);
    ReportingValidationHelper.throwIfIncorrectFileType(file, ALLOWED_FILETYPES);
    ReportingValidationHelper.throwIfFileIsEmpty(file);

    try {
      JasperReport report = JasperCompileManager.compileReport(file.getInputStream());

      String reportType = report.getProperty(REPORT_TYPE_PROPERTY);
      if (reportType != null) {
        jasperTemplate.setType(reportType);
      }

      JRParameter[] jrParameters = report.getParameters();

      if (jrParameters != null && jrParameters.length > 0) {
        processJrParameters(jasperTemplate, jrParameters);
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bos);
      out.writeObject(report);
      jasperTemplate.setData(bos.toByteArray());
    } catch (JRException ex) {
      throw new ReportingException(ex, ERROR_REPORTING_FILE_INVALID);
    } catch (IOException ex) {
      throw new ReportingException(ex, ERROR_REPORTING_IO, ex.getMessage());
    }
  }

  private void processJrParameters(JasperTemplate jasperTemplate, JRParameter[] jrParameters)
      throws ReportingException {
    ArrayList<JasperTemplateParameter> parameters = new ArrayList<>();
    Set<ReportImage> images = new HashSet<>();

    for (JRParameter jrParameter : jrParameters) {
      if (!jrParameter.isSystemDefined()) {
        if (jrParameter.isForPrompting()) {
          JasperTemplateParameter jasperTemplateParameter = createParameter(jrParameter);
          jasperTemplateParameter.setTemplate(jasperTemplate);
          parameters.add(jasperTemplateParameter);
        } else if (Image.class.getName().equals(jrParameter.getValueClassName())) {
          String name = jrParameter.getName();
          ReportImage reportImage = reportImageRepository.findByName(name);
          if (reportImage == null) {
            throw new ReportingException(ReportImageMessageKeys.ERROR_NOT_FOUND_WITH_NAME, name);
          }
          images.add(reportImage);
        }
      }
    }

    jasperTemplate.setTemplateParameters(parameters);
    jasperTemplate.setReportImages(images);
  }

  /**
   * Create new report parameter of report which is not defined in Jasper system.
   */
  private JasperTemplateParameter createParameter(JRParameter jrParameter)
      throws ReportingException {
    String displayName = jrParameter.getPropertiesMap().getProperty("displayName");

    if (isBlank(displayName)) {
      throw new ReportingException(
          ERROR_REPORTING_PARAMETER_MISSING, "displayName");
    }

    String dataType = jrParameter.getValueClassName();
    if (isNotBlank(dataType)) {
      try {
        Class.forName(dataType);
      } catch (ClassNotFoundException err) {
        throw new ReportingException(err, ERROR_REPORTING_PARAMETER_INCORRECT_TYPE,
            jrParameter.getName(), dataType);
      }
    }

    // Set parameters.
    JasperTemplateParameter jasperTemplateParameter = new JasperTemplateParameter();
    jasperTemplateParameter.setName(jrParameter.getName());
    jasperTemplateParameter.setDisplayName(displayName);
    jasperTemplateParameter.setDescription(jrParameter.getDescription());
    jasperTemplateParameter.setDataType(dataType);
    jasperTemplateParameter.setSelectExpression(
        jrParameter.getPropertiesMap().getProperty("selectExpression"));
    jasperTemplateParameter.setSelectProperty(
        jrParameter.getPropertiesMap().getProperty("selectProperty"));
    jasperTemplateParameter.setDisplayProperty(
        jrParameter.getPropertiesMap().getProperty("displayProperty"));
    String required = jrParameter.getPropertiesMap().getProperty("required");
    if (required != null) {
      jasperTemplateParameter.setRequired(Boolean.parseBoolean(
          jrParameter.getPropertiesMap().getProperty("required")));
    }

    if (jrParameter.getDefaultValueExpression() != null) {
      jasperTemplateParameter.setDefaultValue(jrParameter.getDefaultValueExpression()
          .getText().replace("\"", "").replace("\'", ""));
    }

    jasperTemplateParameter.setOptions(extractOptions(jrParameter));
    jasperTemplateParameter.setDependencies(extractDependencies(jrParameter));

    return jasperTemplateParameter;
  }

  private void throwIfTemplateWithSameNameAlreadyExists(String name) throws ReportingException {
    if (jasperTemplateRepository.findByName(name) != null) {
      throw new ReportingException(ERROR_REPORTING_TEMPLATE_EXIST);
    }
  }

  private List<String> extractOptions(JRParameter parameter) {
    return extractListProperties(parameter, "options");
  }

  private List<JasperTemplateParameterDependency> extractDependencies(JRParameter parameter) {
    return extractListProperties(parameter, "dependencies")
        .stream()
        .map(option -> {
          // split by colons
          String[] properties = option.split(":");
          return new JasperTemplateParameterDependency(properties[0], properties[1], properties[2]);
        })
        .collect(Collectors.toList());
  }

  private List<String> extractListProperties(JRParameter parameter, String property) {
    String dependencyProperty = parameter.getPropertiesMap().getProperty(property);

    if (dependencyProperty != null) {
      // split by unescaped commas
      return Arrays
          .stream(dependencyProperty.split("(?<!\\\\),"))
          .map(option -> option.replace("\\,", ","))
          .collect(Collectors.toList());
    }

    return new ArrayList<>();
  }

  private void validateRequiredRights(List<String> rights) {
    for (String right : rights) {
      if (rightReferenceDataService.findRight(right) == null) {
        throw new ValidationMessageException(new Message(ERROR_RIGHT_NOT_FOUND, right));
      }
    }
  }
}
