/*
 * Copyright (c) 2024.  Sweden Connect
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

package se.swedenconnect.sigval.pdf.pdfstruct.impl;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Description
 */
class DefaultPDFSignatureContextTest {

  static byte[] pdfDocBytes;

  @BeforeAll
  static void init() throws Exception {
    try(InputStream pdfStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-doc.pdf")) {
      pdfDocBytes = IOUtils.toByteArray(pdfStream);
    }
  }

  @Test
  void testSignatureContext() throws Exception {
    DefaultPDFSignatureContext signatureContext = new DefaultPDFSignatureContext(pdfDocBytes, new DefaultGeneralSafeObjects());

  }

}