/*
* Copyright 2007 The Apache Software Foundation.
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

package org.apache.axis2.mex.OM;

import org.apache.axiom.om.OMFactory;
import org.apache.axis2.mex.MexConstants;

/**
 * Class implemented for Location element defined in 
 * the WS-MEX spec.
 *
 */

public class Location extends AnyURIType {

	public Location(OMFactory defaultFactory, String namespaceValue, String uri) throws MexOMException {
		super(defaultFactory, namespaceValue, uri);
	}
	public Location(OMFactory defaultFactory, String uri) throws MexOMException {
		super(defaultFactory, MexConstants.Spec_2004_09.NS_URI, uri );
	}
	
	/*
	 * Return name of this element
	 * (non-Javadoc)
	 * @see org.apache.axis2.Mex.OM.AnyURIType#getElementName()
	 */
	protected String getElementName(){
		return MexConstants.SPEC.LOCATION;
	}
}
