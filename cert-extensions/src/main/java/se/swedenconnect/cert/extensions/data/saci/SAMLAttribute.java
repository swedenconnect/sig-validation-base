/*
 * Copyright (c) 2023.  Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.swedenconnect.cert.extensions.data.saci;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.Objects;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SAML Attribute dom implementation
 */
@Data
@NoArgsConstructor
@Slf4j
public class SAMLAttribute extends AbstractDomData {

  public static final String ATTRIBUTE_ELEMENT = "Attribute";
  public static final String ATTRIBUTE_VALUE_ELEMENT = "AttributeValue";

  public static final String NAME = "Name";
  public static final String NAME_FORMAT = "NameFormat";
  public static final String FRIENDLY_NAME = "FriendlyName";

  private String name;
  private String nameFormat;
  private String friendlyName;
  private List<Attr> anyAttrList;
  private List<Element> attributeValues;

  public SAMLAttribute(Element element, boolean strictMode) throws CertificateException {
    super(element, strictMode);
  }

  /** {@inheritDoc} */
  @Override protected void validate() throws CertificateException {
    try {
      if (strictMode){
        Objects.requireNonNull(name, "Name attribute must be present");
      }
    }
    catch (Exception ex) {
      throw new CertificateException(ex);
    }
  }

  @Override public Element getElement(Document document) {
    Element attribute = document.createElementNS(SAML_ASSERTION_NS, ATTRIBUTE_ELEMENT);
    attribute.setPrefix("saml");
    setAttribute(attribute, NAME, name);
    setAttribute(attribute, NAME_FORMAT, nameFormat);
    setAttribute(attribute, FRIENDLY_NAME, friendlyName);
    adoptAttributes(attribute, document, anyAttrList);
    adoptElements(attribute, document, attributeValues);
    return attribute;
  }

  @Override protected void setValuesFromElement(Element element) throws CertificateException {
    this.name = getAttributeValue(element, NAME);
    this.nameFormat = getAttributeValue(element, NAME_FORMAT);
    this.friendlyName = getAttributeValue(element, FRIENDLY_NAME);
    this.anyAttrList = getOtherAttributes(element, List.of(NAME, NAME_FORMAT, FRIENDLY_NAME));
    this.attributeValues = getElements(element, SAML_ASSERTION_NS, ATTRIBUTE_VALUE_ELEMENT);
  }

  public static Element createStringAttributeValue(Document document, String value) {
    Element attrValue = document.createElementNS(AbstractDomData.SAML_ASSERTION_NS,
      SAMLAttribute.ATTRIBUTE_VALUE_ELEMENT);
    attrValue.setPrefix("saml");
    attrValue.setTextContent(value);
    Attr xsiAttr = document.createAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type");
    xsiAttr.setValue("xs:string");
    attrValue.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
    attrValue.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    attrValue.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "xs:string");
    attrValue.setAttributeNode(xsiAttr);
    return attrValue;
  }

}