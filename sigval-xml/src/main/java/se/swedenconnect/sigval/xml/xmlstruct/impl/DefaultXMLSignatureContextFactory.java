/*
 * Copyright (c) 2020. IDsec Solutions AB
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

package se.swedenconnect.sigval.xml.xmlstruct.impl;

import org.w3c.dom.Document;

import se.swedenconnect.sigval.xml.xmlstruct.XMLSignatureContext;
import se.swedenconnect.sigval.xml.xmlstruct.XMLSignatureContextFactory;

import java.io.IOException;

/**
 * Default factory class providing instances of XMLSignatureContext.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class DefaultXMLSignatureContextFactory implements XMLSignatureContextFactory {

  /** {@inheritDoc} */
  @Override public XMLSignatureContext getSignatureContext(Document document) throws IOException {
    return new DefaultXMLSignatureContext(document);
  }
}
