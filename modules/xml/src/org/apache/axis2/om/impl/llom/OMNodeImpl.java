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

package org.apache.axis2.om.impl.llom;

import org.apache.axis2.om.OMContainer;
import org.apache.axis2.om.OMException;
import org.apache.axis2.om.OMNode;
import org.apache.axis2.om.OMXMLParserWrapper;
import org.apache.axis2.om.OMOutputFormat;
import org.apache.axis2.om.impl.OMContainerEx;
import org.apache.axis2.om.impl.OMNodeEx;
import org.apache.axis2.om.impl.OMOutputImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Class OMNodeImpl
 */
public abstract class OMNodeImpl implements OMNode, OMNodeEx {
    /**
     * Field parent
     */
    protected OMContainerEx parent;

    /**
     * Field nextSibling
     */
    protected OMNodeImpl nextSibling;

    /**
     * Field previousSibling
     */
    protected OMNodeImpl previousSibling;
    /**
     * Field builder
     */
    protected OMXMLParserWrapper builder;

    /**
     * Field done
     */
    protected boolean done = false;

    /**
     * Field nodeType
     */
    protected int nodeType;

    /**
     * Constructor OMNodeImpl
     */
    public OMNodeImpl() {
    }

    /**
     * For a node to exist there must be a parent
     *
     * @param parent
     */
    public OMNodeImpl(OMContainer parent) {
        if ((parent != null)) {
            this.parent = (OMContainerEx)parent;
            parent.addChild(this);
        }
    }

    /**
     * This method should return the immediate parent of the node.
     * Parent is always an Element
     *
     * @return
     *
     * @throws OMException
     */
    public OMContainer getParent() {
        return parent;
    }

    /**
     * Method setParent
     *
     * @param element
     */
    public void setParent(OMContainer element) {

        if ((this.parent) == element) {
            return;
        }

        //If we are asked to assign a new parent in place 
        //of an existing one. We should detach this node
        //from the aegis of previous parent.
        if (this.parent != null) {
            this.detach();
        }
        this.parent = (OMContainerEx)element;
    }

    /**
     * This will give the next sibling. This can be an OMAttribute for OMAttribute or OMText or OMELement for others.
     *
     * @return
     * @throws org.apache.axis2.om.OMException
     *
     * @throws OMException
     */
    public OMNode getNextOMSibling() throws OMException {
        if ((nextSibling == null) && (parent != null) && !parent.isComplete()) {
            parent.buildNext();
        }
        return nextSibling;
    }

    /**
     * Method setNextOMSibling
     *
     * @param node
     */
    public void setNextOMSibling(OMNode node) {
        this.nextSibling = (OMNodeImpl) node;
    }


    /**
     * this will indicate whether parser has parsed this information item completely or not.
     * If somethings info are not available in the item, one has to check this attribute to make sure that, this
     * item has been parsed completely or not.
     *
     * @return boolean
     */
    public boolean isComplete() {
        return done;
    }

    /**
     * Method setComplete
     *
     * @param state
     */
    public void setComplete(boolean state) {
        this.done = state;
    }

    /**
     * This will remove this information item and its children, from the model completely
     *
     * @throws org.apache.axis2.om.OMException
     *
     * @throws OMException
     */
    public OMNode detach() throws OMException {
        if (parent == null) {
            throw new OMException(
                    "Elements that doesn't have a parent can not be detached");
        }
        OMNodeImpl nextSibling = (OMNodeImpl) getNextOMSibling();
        if (previousSibling == null) {
            parent.setFirstChild(nextSibling);
        } else {
            ((OMNodeEx)getPreviousOMSibling()).setNextOMSibling(nextSibling);
        }
        if (nextSibling != null) {
            nextSibling.setPreviousOMSibling(getPreviousOMSibling());
        }
        this.parent = null;
        return this;
    }

    /**
     * This will insert a sibling just after the current information item.
     *
     * @param sibling
     * @throws org.apache.axis2.om.OMException
     *
     * @throws OMException
     */
    public void insertSiblingAfter(OMNode sibling) throws OMException {
        if (parent == null) {
            throw new OMException();
        }
        ((OMNodeEx)sibling).setParent(parent);
        if (sibling instanceof OMNodeImpl) {
            OMNodeImpl siblingImpl = (OMNodeImpl) sibling;
            if (nextSibling == null) {
                getNextOMSibling();
            }
            siblingImpl.setPreviousOMSibling(this);
            if (nextSibling != null) {
                nextSibling.setPreviousOMSibling(sibling);
            }
            ((OMNodeEx)sibling).setNextOMSibling(nextSibling);
            nextSibling = siblingImpl;
        }
    }

    /**
     * This will insert a sibling just before the current information item
     *
     * @param sibling
     * @throws org.apache.axis2.om.OMException
     *
     * @throws OMException
     */
    public void insertSiblingBefore(OMNode sibling) throws OMException {
        if (parent == null) {
            throw new OMException();
        }
        ((OMNodeEx)sibling).setParent(parent);
        if (sibling instanceof OMNodeImpl) {
            OMNodeImpl siblingImpl = (OMNodeImpl) sibling;
            siblingImpl.setPreviousOMSibling(previousSibling);
            siblingImpl.setNextOMSibling(this);
            if (previousSibling == null) {
                parent.setFirstChild(siblingImpl);
            } else {
                previousSibling.setNextOMSibling(siblingImpl);
            }
            previousSibling = siblingImpl;
        }
    }

    /**
     * This is to get the type of node, as this is the super class of all the nodes
     *
     * @return Returns the type of node as indicated by {@link #setType}
     *
     * @see #setType
     */
    public int getType() {
        return nodeType;
    }

    /**
     * Method setType
     *
     * @param nodeType
     * @throws OMException
     */
    public void setType(int nodeType) throws OMException {
        this.nodeType = nodeType;
    }

    /**
     * Method getPreviousOMSibling
     *
     * @return boolean
     */
    public OMNode getPreviousOMSibling() {
        return previousSibling;
    }

    /**
     * Method setPreviousOMSibling
     *
     * @param previousSibling
     */
    public void setPreviousOMSibling(OMNode previousSibling) {
        this.previousSibling = (OMNodeImpl) previousSibling;
    }


    /**
     * This will completely parse this node and build the object structure in the memory.
     * However a programmatically created node will have done set to true by default and will cause
     * populateyourself not to work properly!
     *
     * @throws OMException
     */
    public void build() throws OMException {
        while (!done) {
            builder.next();
        }
    }

    /**
     * Serialize the node with caching
     *
     * @param xmlWriter
     * @throws javax.xml.stream.XMLStreamException
     *
     * @see #serialize(org.apache.axis2.om.impl.OMOutputImpl)
     */
    public void serialize(XMLStreamWriter xmlWriter) throws XMLStreamException {
        OMOutputImpl omOutput = new OMOutputImpl(xmlWriter);
        serialize(omOutput);
        omOutput.flush();
    }

    /**
     * Serialize the node without caching
     *
     * @param xmlWriter
     * @throws javax.xml.stream.XMLStreamException
     *
     * @see #serializeAndConsume(org.apache.axis2.om.impl.OMOutputImpl)
     */
    public void serializeAndConsume(XMLStreamWriter xmlWriter) throws XMLStreamException {
        OMOutputImpl omOutput = new OMOutputImpl(xmlWriter);
        serializeAndConsume(omOutput);
        omOutput.flush();
    }

    /**
     * Serialize the node with caching
     *
     * @param omOutput
     * @throws XMLStreamException
     * @see #serialize(org.apache.axis2.om.impl.OMOutputImpl)
     */
    public void serialize(OMOutputImpl omOutput) throws XMLStreamException {
        throw new RuntimeException("Not implemented yet!");
    }

    /**
     * Serialize the node without caching
     *
     * @param omOutput
     * @throws XMLStreamException
     * @see #serializeAndConsume(org.apache.axis2.om.impl.OMOutputImpl)
     */
    public void serializeAndConsume(OMOutputImpl omOutput) throws XMLStreamException {
        throw new RuntimeException("Not implemented yet!");
    }

    public void serialize(OutputStream output) throws XMLStreamException {
        serialize(XMLOutputFactory.newInstance().createXMLStreamWriter(output));
    }

    public void serialize(Writer writer) throws XMLStreamException {
        serialize(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
    }

    public void serializeAndConsume(OutputStream output) throws XMLStreamException {
        serializeAndConsume(XMLOutputFactory.newInstance().createXMLStreamWriter(output));
    }

    public void serializeAndConsume(Writer writer) throws XMLStreamException {
        serializeAndConsume(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
    }

    public void serialize(OutputStream output, OMOutputFormat format) throws XMLStreamException {    
        OMOutputImpl omOutput = new  OMOutputImpl(output, format);
        serialize(omOutput);
        omOutput.flush();
    }

    public void serialize(Writer writer, OMOutputFormat format) throws XMLStreamException {
        OMOutputImpl omOutput = new  OMOutputImpl(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
        omOutput.setOutputFormat(format);
        serialize(omOutput);
        omOutput.flush();
    }

    public void serializeAndConsume(OutputStream output, OMOutputFormat format) throws XMLStreamException {
        OMOutputImpl omOutput = new  OMOutputImpl(output, format);
        serializeAndConsume(omOutput);
        omOutput.flush();
    }

    public void serializeAndConsume(Writer writer, OMOutputFormat format) throws XMLStreamException {
        OMOutputImpl omOutput = new  OMOutputImpl(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
        omOutput.setOutputFormat(format);
        serializeAndConsume(omOutput);
        omOutput.flush();
    }
}
