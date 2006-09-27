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

package org.apache.rampart;

import org.apache.axiom.om.OMElement;
import org.apache.rahas.Token;
import org.apache.rahas.TokenStorage;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.message.token.Reference;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.namespace.QName;

import java.io.IOException;


public class TokenCallbackHandler implements CallbackHandler {

    private TokenStorage store;

    
    public TokenCallbackHandler(TokenStorage store) {
        this.store = store;
    }
    
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (int i = 0; i < callbacks.length; i++) {

            if (callbacks[i] instanceof WSPasswordCallback) {
                WSPasswordCallback pc = (WSPasswordCallback) callbacks[i];
                String id = pc.getIdentifer();
                Token tok;
                try {
                    //Pick up the token from the token store
                    tok = this.store.getToken(id);
                    if(tok != null) {
                        //Get the secret and set it in the callback object
                        pc.setKey(tok.getSecret());
                    } else {
                        //Try the unattached refs
                        Token[] tokens = store.getValidTokens();
                        for (int j = 0; j < tokens.length; j++) {
                            OMElement elem = tokens[j].getAttachedReference();
                            if(elem != null && id.equals(this.getIdFromSTR(elem))) {
                                pc.setKey(tokens[j].getSecret());
                                return;
                            }
                            elem = tokens[j].getUnattachedReference();
                            if(elem != null && id.equals(this.getIdFromSTR(elem))) {
                                pc.setKey(tokens[j].getSecret());
                                return;
                            }
                            
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IOException(e.getMessage());
                }

            } else {
                throw new UnsupportedCallbackException(callbacks[i],
                        "Unrecognized Callback");
            }
        }
    }
    
    private String getIdFromSTR(OMElement str) {
//      ASSUMPTION:SecurityTokenReference/KeyIdentifier
        OMElement child = str.getFirstElement();
        if(child == null) {
            return null;
        }
        
        if (child.getQName().equals(new QName(WSConstants.SIG_NS, "KeyInfo"))) {
            return child.getText();
        } else if(child.getQName().equals(Reference.TOKEN)) {
            return child.getAttributeValue(new QName("URI"));
        } else {
            return null;
        }
    }

}
