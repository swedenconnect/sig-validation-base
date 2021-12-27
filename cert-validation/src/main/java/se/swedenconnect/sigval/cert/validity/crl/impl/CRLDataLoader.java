/*
 * Copyright (c) 2021. Sweden Connect
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

package se.swedenconnect.sigval.cert.validity.crl.impl;

import java.io.IOException;

public interface CRLDataLoader {

  /**
   * Download CRL data
   * @param url URL from which the CRL is to be downloaded
   * @param connectTimeout timeout in milliseconds for connecting to the CRL source
   * @param readTimeout timout in milliseconds for reading the CRL data
   * @return CRL bytes
   * @throws IOException on errors downloading the CRL
   */
  byte[] downloadCrl(String url, int connectTimeout, int readTimeout) throws IOException;
}
