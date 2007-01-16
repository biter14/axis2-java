/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.axis2.transport;

import java.io.OutputStream;
import java.net.URL;

import org.apache.axiom.om.OMOutputFormat;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;

/**
 * <p>
 * MessageFormatter implementations are used by Axis2 to support serialization
 * of messages to different message formats. (Eg: JSON). Users can register
 * MessageFormatter implementations against a message type using the axis2.xml.
 * Message type for a message can be specified by setting the "messageType"
 * property in the MessageContext. This can also be given as a parameter in the
 * service.xml/axis2.xml for a per service based/engine wide configuration.
 * </p>
 * <p>
 * <messageFormatters> 
 * 		<messageFormatter contentType="application/soap+xml"
 * 			class="org.apache.axis2.transport.http.SOAPMessageFormatter"/>
 * </messageFormatters>
 * </p>
 */
public interface MessageFormatter {
	
	/**
	 * @return a byte array of the message formatted according to the given
	 *         message format.
	 */
	public byte[] getBytes(MessageContext messageContext, OMOutputFormat format)
			throws AxisFault;

	/**
	 * To support deffered writing transports as in http chunking.. Axis2 was
	 * doing this for some time.. TODO: Clarify the real usage of it
	 * 
	 * @param out
	 * @param preserve :  do not consume the OM when this is set..
	 */
	public void writeTo(MessageContext messageContext, OMOutputFormat format,
			OutputStream outputStream, boolean preserve) throws AxisFault;

	public String getContentType(MessageContext messageContext, OMOutputFormat format,
			String soapAction);

	/**
	 * Some message formats may want to alter the target url.
	 * 
	 * @return the target URL
	 */
	public URL getTargetAddress(MessageContext messageContext, OMOutputFormat format,
			URL targetURL);

	/**
	 * @return this only if you want set a transport header for SOAP Action
	 */
	public String formatSOAPAction(MessageContext messageContext, OMOutputFormat format,
			String soapAction);
}
