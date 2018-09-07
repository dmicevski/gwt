/*
 * Copyright 2006 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.user.client.rpc.core.java.util;

import com.google.gwt.user.client.rpc.CustomFieldSerializer;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamReader;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;

import java.util.Date;

/**
 * Custom field serializer for {@link java.util.Date}.
 */
public final class Date_CustomFieldSerializer extends
    CustomFieldSerializer<Date> {

  /**
   * @param streamReader a SerializationStreamReader instance
   * @param instance the instance to be deserialized
   */
  public static void deserialize(SerializationStreamReader streamReader,
      Date instance) {
    // No fields
  }

  public static Date instantiate(SerializationStreamReader streamReader)
      throws SerializationException {
    long time = streamReader.readLong(); //GMT
    
    long offset = (new Date(time).getTimezoneOffset() * 60 * 1000);
	
    // The instance date is in UTC format so add back the client's timezone offset back to get it into GMT format.
    Date date = new Date(time + offset);  //AUS

    return date;
  }

  public static void serialize(SerializationStreamWriter streamWriter,
      Date instance) throws SerializationException {
    long offset = (instance.getTimezoneOffset() * 60 * 1000) * 2L; // Need to double the offset
	  
    // The instance date is in GMT format so subtract the client's timezone offset to get it into UTC format.
    streamWriter.writeLong(instance.getTime() - offset); //GMT
  }

  @Override
  public void deserializeInstance(SerializationStreamReader streamReader,
      Date instance) throws SerializationException {
    deserialize(streamReader, instance);
  }

  @Override
  public boolean hasCustomInstantiateInstance() {
    return true;
  }

  @Override
  public Date instantiateInstance(SerializationStreamReader streamReader)
      throws SerializationException {
    return instantiate(streamReader);
  }

  @Override
  public void serializeInstance(SerializationStreamWriter streamWriter,
      Date instance) throws SerializationException {
    serialize(streamWriter, instance);
  }
}
