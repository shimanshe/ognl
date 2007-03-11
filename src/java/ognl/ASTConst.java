// --------------------------------------------------------------------------
// Copyright (c) 1998-2004, Drew Davidson and Luke Blanshard
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// Neither the name of the Drew Davidson nor the names of its contributors
// may be used to endorse or promote products derived from this software
// without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
// BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
// OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
// AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
// THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
// --------------------------------------------------------------------------
package ognl;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author Luke Blanshard (blanshlu@netscape.net)
 * @author Drew Davidson (drew@ognl.org)
 */
public class ASTConst extends SimpleNode implements NodeType
{

    private Object value;
    
    private Class _getterClass;
    
    public ASTConst(int id)
    {
        super(id);
    }

    public ASTConst(OgnlParser p, int id)
    {
        super(p, id);
    }

    /** Called from parser actions. */
    public void setValue(Object value)
    {
        this.value = value;
    }
    
    public Object getValue()
    {
        return value;
    }

    protected Object getValueBody(OgnlContext context, Object source)
        throws OgnlException
    {
        return this.value;
    }

    public boolean isNodeConstant(OgnlContext context)
        throws OgnlException
    {
        return true;
    }
    
    public Class getGetterClass()
    {
        if (_getterClass == null)
            return null;
        
        return _getterClass;
    }
    
    public Class getSetterClass()
    {
        return null;
    }
    
    public String toString()
    {
        String result;

        if (value == null) {
            result = "null";
        } else {
            if (value instanceof String) {
                result = '\"' + OgnlOps.getEscapeString(value.toString()) + '\"';
            } else {
                if (value instanceof Character) {
                    result = '\'' + OgnlOps.getEscapedChar(((Character) value).charValue()) + '\'';
                } else {
                    result = value.toString();
                    if (value instanceof Long) {
                        result = result + "L";
                    } else {
                        if (value instanceof BigDecimal) {
                            result = result + "B";
                        } else {
                            if (value instanceof BigInteger) {
                                result = result + "H";
                            } else {
                                if (value instanceof Node) {
                                    result = ":[ " + result + " ]";
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
    
    public String toGetSourceString(OgnlContext context, Object target)
    {
        if (value == null)
            return "";
        
        _getterClass = value.getClass();
        context.setCurrentType(value.getClass());
        context.setCurrentObject(value);
        
        if (_parent != null && ComparisonExpression.class.isAssignableFrom(_parent.getClass())
                && value != null
                && Number.class.isAssignableFrom(value.getClass())) {
            
            context.setCurrentType(OgnlRuntime.getPrimitiveWrapperClass(value.getClass()));
            
        } else if (_parent == null || !ASTProperty.class.isInstance(_parent))
            context.setCurrentType(_getterClass);
        
        /* if (_parent == null && Number.class.isAssignableFrom(value.getClass())) {
            
            if (BigInteger.class.isInstance(value)) {
                
                return "new java.math.BigInteger(\"" + value + "\")";
            }
            
            Class clazz = value.getClass();
            if (clazz.isPrimitive())
                clazz = OgnlRuntime.getPrimitiveWrapperClass(value.getClass());
            
            return "new " + clazz.getName() + "(" + OgnlRuntime.getNumericCast(value.getClass()) + value +")";
        } else  if (Number.class.isAssignableFrom(value.getClass()) && OgnlRuntime.getNumericLiteral(value.getClass()) != null) {
            
            return value.toString() + OgnlRuntime.getNumericLiteral(value.getClass());
        } else */ if (!(_parent != null 
                && NumericExpression.class.isAssignableFrom(_parent.getClass()))
                && String.class.isAssignableFrom(value.getClass())) {
            
            return '\"' + OgnlOps.getEscapeString(value.toString()) + '\"';
        } else if (Character.class.isInstance(value)) {
            
            Character val = (Character)value;
            
            if (Character.isLetterOrDigit(val.charValue()))
               return "'" + ((Character) value).charValue() + "'";
            else
                return "'" + OgnlOps.getEscapedChar(((Character) value).charValue()) + "'";
        }
        
        if (Boolean.class.isAssignableFrom(value.getClass()))
            _getterClass = Boolean.TYPE;
        
        context.setCurrentType(value.getClass());
        context.setCurrentObject(value);

        return value.toString();
    }
}