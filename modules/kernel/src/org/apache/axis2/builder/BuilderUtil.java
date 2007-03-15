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

package org.apache.axis2.builder;

import org.apache.axiom.attachments.Attachments;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.MTOMConstants;
import org.apache.axiom.om.impl.builder.StAXBuilder;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.impl.builder.XOPAwareStAXOMBuilder;
import org.apache.axiom.om.util.StAXUtils;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPConstants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPProcessingException;
import org.apache.axiom.soap.impl.builder.MTOMStAXSOAPModelBuilder;
import org.apache.axiom.soap.impl.builder.StAXSOAPModelBuilder;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisMessage;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.util.MultipleEntryHashMap;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.ws.commons.schema.XmlSchemaComplexType;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaParticle;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.XmlSchemaType;

import javax.xml.namespace.QName;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.util.Iterator;

public class BuilderUtil {
    public static final int BOM_SIZE = 4;

    public static SOAPEnvelope buildsoapMessage(MessageContext messageContext,
                                                MultipleEntryHashMap requestParameterMap,
                                                SOAPFactory soapFactory) throws AxisFault {

        SOAPEnvelope soapEnvelope = soapFactory.getDefaultEnvelope();
        SOAPBody body = soapEnvelope.getBody();
        XmlSchemaElement xmlSchemaElement = null;
        AxisOperation axisOperation = messageContext.getAxisOperation();
        if (axisOperation != null) {
            AxisMessage axisMessage =
                    axisOperation.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
            xmlSchemaElement = axisMessage.getSchemaElement();
        }

        if (xmlSchemaElement == null) {
            // if there is no schema its piece of cake !! add these to the soap body in any order you like.
            // Note : if there are parameters in the path of the URL, there is no way this can add them
            // to the message.
            OMElement bodyFirstChild =
                    soapFactory.createOMElement(messageContext.getAxisOperation().getName(), body);

            // first add the parameters in the URL
            if (requestParameterMap != null) {
                Iterator requestParamMapIter = requestParameterMap.keySet().iterator();
                while (requestParamMapIter.hasNext()) {
                    String key = (String) requestParamMapIter.next();
                    String value = (String) requestParameterMap.get(key);
                    soapFactory.createOMElement(key, null, bodyFirstChild).setText(value);

                }
            }
        } else {

            // first get the target namespace from the schema and the wrapping element.
            // create an OMElement out of those information. We are going to extract parameters from
            // url, create OMElements and add them as children to this wrapping element.
            String targetNamespace = xmlSchemaElement.getQName().getNamespaceURI();
            QName bodyFirstChildQName;
            if (targetNamespace != null && !"".equals(targetNamespace)) {
                bodyFirstChildQName = new QName(targetNamespace, xmlSchemaElement.getName());
            } else {
                bodyFirstChildQName = new QName(xmlSchemaElement.getName());
            }
            OMElement bodyFirstChild = soapFactory.createOMElement(bodyFirstChildQName, body);

            // Schema should adhere to the IRI style in this. So assume IRI style and dive in to
            // schema
            XmlSchemaType schemaType = xmlSchemaElement.getSchemaType();
            if (schemaType instanceof XmlSchemaComplexType) {
                XmlSchemaComplexType complexType = ((XmlSchemaComplexType) schemaType);
                XmlSchemaParticle particle = complexType.getParticle();
                if (particle instanceof XmlSchemaSequence) {
                    XmlSchemaSequence xmlSchemaSequence = (XmlSchemaSequence) particle;
                    Iterator iterator = xmlSchemaSequence.getItems().getIterator();

                    // now we need to know some information from the binding operation.

                    while (iterator.hasNext()) {
                        XmlSchemaElement innerElement = (XmlSchemaElement) iterator.next();
                        QName qName = innerElement.getQName();
                        long minOccurs = innerElement.getMinOccurs();
                        boolean nillable = innerElement.isNillable();
                        String name =
                                qName != null ? qName.getLocalPart() : innerElement.getName();
                        String value;
                        OMNamespace ns = (qName == null ||
                                qName.getNamespaceURI() == null
                                || qName.getNamespaceURI().length() == 0) ?
                                null : soapFactory.createOMNamespace(
                                qName.getNamespaceURI(), null);
                        while ((value = (String) requestParameterMap.get(name)) != null) {

                            soapFactory.createOMElement(name, ns,
                                                        bodyFirstChild).setText(value);
                            minOccurs--;
                        }
                        if (minOccurs > 0) {
                            if (nillable) {

                                OMNamespace xsi = soapFactory.createOMNamespace(
                                        Constants.URI_DEFAULT_SCHEMA_XSI,
                                        Constants.NS_PREFIX_SCHEMA_XSI);
                                OMAttribute omAttribute =
                                        soapFactory.createOMAttribute("nil", xsi, "true");
                                soapFactory.createOMElement(name, ns,
                                                            bodyFirstChild)
                                        .addAttribute(omAttribute);

                            } else {
                                throw new AxisFault("Required element " + qName +
                                        " defined in the schema can not be found in the request");
                            }
                        }
                    }
                }
            }
        }
        return soapEnvelope;
    }

    public static StAXBuilder getPOXBuilder(InputStream inStream, String charSetEnc)
            throws XMLStreamException {
        StAXBuilder builder;
        XMLStreamReader xmlreader =
                StAXUtils.createXMLStreamReader(inStream, charSetEnc);
        builder = new StAXOMBuilder(xmlreader);
        return builder;
    }

    /**
     * Use the BOM Mark to identify the encoding to be used. Fall back to
     * default encoding specified
     *
     * @param is
     * @param charSetEncoding
     * @throws java.io.IOException
     */
    public static Reader getReader(InputStream is, String charSetEncoding) throws IOException {
        PushbackInputStream is2 = new PushbackInputStream(is, BOM_SIZE);
        String encoding;
        byte bom[] = new byte[BOM_SIZE];
        int n, unread;

        n = is2.read(bom, 0, bom.length);

        if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
            encoding = "UTF-8";
            unread = n - 3;
        } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
            encoding = "UTF-16BE";
            unread = n - 2;
        } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
            encoding = "UTF-16LE";
            unread = n - 2;
        } else if ((bom[0] == (byte) 0x00) && (bom[1] == (byte) 0x00) && (bom[2] == (byte) 0xFE)
                && (bom[3] == (byte) 0xFF)) {
            encoding = "UTF-32BE";
            unread = n - 4;
        } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE) && (bom[2] == (byte) 0x00)
                && (bom[3] == (byte) 0x00)) {
            encoding = "UTF-32LE";
            unread = n - 4;
        } else {

            // Unicode BOM mark not found, unread all bytes
            encoding = charSetEncoding;
            unread = n;
        }

        if (unread > 0) {
            is2.unread(bom, (n - unread), unread);
        }

        return new BufferedReader(new InputStreamReader(is2, encoding));
    }


    public static String getEnvelopeNamespace(String contentType) {
        String soapNS = SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI;
        if (contentType != null) {
            if (contentType.indexOf(SOAP12Constants.SOAP_12_CONTENT_TYPE) > -1) {
                // it is SOAP 1.2
                soapNS = SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI;
            } else if (contentType.indexOf(SOAP11Constants.SOAP_11_CONTENT_TYPE) > -1) {
                // SOAP 1.1
                soapNS = SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI;
            }
        }
        return soapNS;
    }

    /**
     * Extracts and returns the character set encoding from the
     * Content-type header
     * Example:
     * Content-Type: text/xml; charset=utf-8
     *
     * @param contentType
     */
    public static String getCharSetEncoding(String contentType) {
        if (contentType == null) {
            // Using the default UTF-8
            return MessageContext.DEFAULT_CHAR_SET_ENCODING;
        }

        int index = contentType.indexOf(HTTPConstants.CHAR_SET_ENCODING);

        if (index == -1) {    // Charset encoding not found in the content-type header
            // Using the default UTF-8
            return MessageContext.DEFAULT_CHAR_SET_ENCODING;
        }

        // If there are spaces around the '=' sign
        int indexOfEq = contentType.indexOf("=", index);

        // There can be situations where "charset" is not the last parameter of the Content-Type header
        int indexOfSemiColon = contentType.indexOf(";", indexOfEq);
        String value;

        if (indexOfSemiColon > 0) {
            value = (contentType.substring(indexOfEq + 1, indexOfSemiColon));
        } else {
            value = (contentType.substring(indexOfEq + 1, contentType.length())).trim();
        }

        // There might be "" around the value - if so remove them
        if (value.indexOf('\"') != -1) {
            value = value.replaceAll("\"", "");
        }

        return value.trim();
    }

    public static StAXBuilder getAttachmentsBuilder(MessageContext msgContext,
                                                    InputStream inStream, String contentTypeString,
                                                    boolean isSOAP)
            throws OMException, XMLStreamException, FactoryConfigurationError {
        StAXBuilder builder = null;
        XMLStreamReader streamReader;

        Attachments attachments = createAttachmentsMap(msgContext, inStream, contentTypeString);
        String charSetEncoding = getCharSetEncoding(attachments.getSOAPPartContentType());

        if ((charSetEncoding == null)
                || "null".equalsIgnoreCase(charSetEncoding)) {
            charSetEncoding = MessageContext.UTF_8;
        }
        msgContext.setProperty(Constants.Configuration.CHARACTER_SET_ENCODING,
                               charSetEncoding);

        try {
            streamReader = StAXUtils.createXMLStreamReader(getReader(
                    attachments.getSOAPPartInputStream(), charSetEncoding));
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }

        //  Put a reference to Attachments Map in to the message context For
        // backword compatibility with Axis2 1.0 
        msgContext.setProperty(MTOMConstants.ATTACHMENTS, attachments);

        // Setting the Attachments map to new SwA API
        msgContext.setAttachmentMap(attachments);

        String soapEnvelopeNamespaceURI = getEnvelopeNamespace(contentTypeString);

        if (isSOAP) {
            if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.MTOM_TYPE)) {
                //Creates the MTOM specific MTOMStAXSOAPModelBuilder
                builder = new MTOMStAXSOAPModelBuilder(streamReader,
                                                       attachments, soapEnvelopeNamespaceURI);
                msgContext.setDoingMTOM(true);
            } else if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.SWA_TYPE)) {
                builder = new StAXSOAPModelBuilder(streamReader,
                                                   soapEnvelopeNamespaceURI);
            } else if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.SWA_TYPE_12)) {
                builder = new StAXSOAPModelBuilder(streamReader,
                                                   soapEnvelopeNamespaceURI);
            }

        }
        // To handle REST XOP case
        else {
            if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.MTOM_TYPE)) {
                XOPAwareStAXOMBuilder stAXOMBuilder = new XOPAwareStAXOMBuilder(
                        streamReader, attachments);
                builder = stAXOMBuilder;

            } else if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.SWA_TYPE)) {
                builder = new StAXOMBuilder(streamReader);
            } else if (attachments.getAttachmentSpecType().equals(
                    MTOMConstants.SWA_TYPE_12)) {
                builder = new StAXOMBuilder(streamReader);
            }
        }

        return builder;
    }

    protected static Attachments createAttachmentsMap(MessageContext msgContext,
                                                      InputStream inStream,
                                                      String contentTypeString) {
        Object cacheAttachmentProperty = msgContext
                .getProperty(Constants.Configuration.CACHE_ATTACHMENTS);
        String cacheAttachmentString = null;
        boolean fileCacheForAttachments;

        if (cacheAttachmentProperty != null
                && cacheAttachmentProperty instanceof String) {
            cacheAttachmentString = (String) cacheAttachmentProperty;
            fileCacheForAttachments = (Constants.VALUE_TRUE
                    .equals(cacheAttachmentString));
        } else {
            Parameter parameter_cache_attachment = msgContext
                    .getParameter(Constants.Configuration.CACHE_ATTACHMENTS);
            cacheAttachmentString =
                    (parameter_cache_attachment != null) ? (String) parameter_cache_attachment
                            .getValue()
                            : null;
        }
        fileCacheForAttachments = (Constants.VALUE_TRUE
                .equals(cacheAttachmentString));

        String attachmentRepoDir = null;
        String attachmentSizeThreshold = null;

        if (fileCacheForAttachments) {
            Object attachmentRepoDirProperty = msgContext
                    .getProperty(Constants.Configuration.ATTACHMENT_TEMP_DIR);

            if (attachmentRepoDirProperty != null) {
                attachmentRepoDir = (String) attachmentRepoDirProperty;
            } else {
                Parameter attachmentRepoDirParameter = msgContext
                        .getParameter(Constants.Configuration.ATTACHMENT_TEMP_DIR);
                attachmentRepoDir =
                        (attachmentRepoDirParameter != null) ? (String) attachmentRepoDirParameter
                                .getValue()
                                : null;
            }

            Object attachmentSizeThresholdProperty = msgContext
                    .getProperty(Constants.Configuration.FILE_SIZE_THRESHOLD);
            if (attachmentSizeThresholdProperty != null
                    && attachmentSizeThresholdProperty instanceof String) {
                attachmentSizeThreshold = (String) attachmentSizeThresholdProperty;
            } else {
                Parameter attachmentSizeThresholdParameter = msgContext
                        .getParameter(Constants.Configuration.FILE_SIZE_THRESHOLD);
                attachmentSizeThreshold = attachmentSizeThresholdParameter
                        .getValue().toString();
            }
        }

        Attachments attachments = new Attachments(inStream, contentTypeString,
                                                  fileCacheForAttachments, attachmentRepoDir,
                                                  attachmentSizeThreshold);
        return attachments;
    }

    /**
     * @param in
     * @return
     * @throws XMLStreamException
     * @deprecated If some one really need this method, please shout.
     */
    public static StAXBuilder getBuilder(Reader in) throws XMLStreamException {
        XMLStreamReader xmlreader = StAXUtils.createXMLStreamReader(in);
        StAXBuilder builder = new StAXSOAPModelBuilder(xmlreader, null);
        return builder;
    }

    /**
     * Creates an OMBuilder for a plain XML message. Default character set encording is used.
     *
     * @param inStream InputStream for a XML message
     * @return Handler to a OMBuilder implementation instance
     * @throws XMLStreamException
     */
    public static StAXBuilder getBuilder(InputStream inStream) throws XMLStreamException {
        XMLStreamReader xmlReader = StAXUtils.createXMLStreamReader(inStream);
        return new StAXOMBuilder(xmlReader);
    }

    /**
     * Creates an OMBuilder for a plain XML message.
     *
     * @param inStream   InputStream for a XML message
     * @param charSetEnc Character set encoding to be used
     * @return Handler to a OMBuilder implementation instance
     * @throws XMLStreamException
     */
    public static StAXBuilder getBuilder(InputStream inStream, String charSetEnc)
            throws XMLStreamException {
        XMLStreamReader xmlReader = StAXUtils.createXMLStreamReader(inStream, charSetEnc);
        return new StAXOMBuilder(xmlReader);
    }

    /**
     * Creates an OMBuilder for a SOAP message. Default character set encording is used.
     *
     * @param inStream InputStream for a SOAP message
     * @return Handler to a OMBuilder implementation instance
     * @throws XMLStreamException
     */
    public static StAXBuilder getSOAPBuilder(InputStream inStream) throws XMLStreamException {
        XMLStreamReader xmlreader = StAXUtils.createXMLStreamReader(inStream);
        return new StAXSOAPModelBuilder(xmlreader);
    }

    /**
     * Creates an OMBuilder for a SOAP message.
     *
     * @param inStream   InputStream for a SOAP message
     * @param charSetEnc Character set encoding to be used
     * @return Handler to a OMBuilder implementation instance
     * @throws XMLStreamException
     */
    public static StAXBuilder getSOAPBuilder(InputStream inStream, String charSetEnc)
            throws XMLStreamException {
        XMLStreamReader xmlreader = StAXUtils.createXMLStreamReader(inStream, charSetEnc);
        return new StAXSOAPModelBuilder(xmlreader);
    }

    public static StAXBuilder getBuilder(SOAPFactory soapFactory, InputStream in, String charSetEnc)
            throws XMLStreamException {
        StAXBuilder builder;
        XMLStreamReader xmlreader = StAXUtils.createXMLStreamReader(in, charSetEnc);
        builder = new StAXOMBuilder(soapFactory, xmlreader);
        return builder;
    }

    /**
     * Initial work for a builder selector which selects the builder for a given
     * message format based on the the content type of the recieved message.
     * content-type to builder mapping can be specified through the Axis2.xml.
     *
     * @param type
     * @param msgContext
     * @return the builder registered against the given content-type
     * @throws AxisFault
     */
    public static Builder getBuilderFromSelector(String type, MessageContext msgContext)
            throws AxisFault {

        Builder builder = msgContext.getConfigurationContext().getAxisConfiguration()
                .getMessageBuilder(type);
        if (builder != null) {
            // Setting the received content-type as the messageType to make
            // sure that we respond using the received message serialisation
            // format.
            msgContext.setProperty(Constants.Configuration.MESSAGE_TYPE, type);
        }
        return builder;
    }

    public static void validateSOAPVersion(String soapNamespaceURIFromTransport,
                                           SOAPEnvelope envelope) {
        if (soapNamespaceURIFromTransport != null) {
            OMNamespace envelopeNamespace = envelope.getNamespace();
            String namespaceName = envelopeNamespace.getNamespaceURI();
            if (!(soapNamespaceURIFromTransport.equals(namespaceName))) {
                throw new SOAPProcessingException(
                        "Transport level information does not match with SOAP" +
                                " Message namespace URI", envelopeNamespace.getPrefix() + ":" +
                        SOAPConstants.FAULT_CODE_VERSION_MISMATCH);
            }
        }
    }

    public static void validateCharSetEncoding(String charsetEncodingFromTransport,
                                               String charsetEncodingFromXML,
                                               String soapNamespaceURI) throws AxisFault {
        if ((charsetEncodingFromXML != null)
                && !"".equals(charsetEncodingFromXML)
                && (charsetEncodingFromTransport != null)
                && !charsetEncodingFromXML.equalsIgnoreCase((String) charsetEncodingFromTransport))
        {
            String faultCode;

            if (SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI.equals(soapNamespaceURI)) {
                faultCode = SOAP12Constants.FAULT_CODE_SENDER;
            } else {
                faultCode = SOAP11Constants.FAULT_CODE_SENDER;
            }

            throw new AxisFault("Character Set Encoding from "
                    + "transport information do not match with "
                    + "character set encoding in the received SOAP message", faultCode);
        }
    }
}
