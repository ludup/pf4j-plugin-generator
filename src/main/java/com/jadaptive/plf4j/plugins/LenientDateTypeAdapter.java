package com.jadaptive.plf4j.plugins;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class LenientDateTypeAdapter extends TypeAdapter<Date> {

	    public static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {

	        @SuppressWarnings("unchecked") // we use a runtime check to make sure the 'T's equal
	        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
	            return typeToken.getRawType() == Date.class ? (TypeAdapter<T>) new LenientDateTypeAdapter() : null;
	        }

	    };

	    private final DateFormat enUsFormat
	            = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.US);
	    private final DateFormat localFormat
	            = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT);
	    private final DateFormat iso8601Format = buildIso8601Format();
	    private final DateFormat shortFormat = buildShortFormat();

	    private static DateFormat buildIso8601Format() {
	        DateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
	        iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
	        return iso8601Format;
	    }

	    private static DateFormat buildShortFormat() {
	        DateFormat shortFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
	        shortFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	        return shortFormat;
	    }

	    @Override
	    public Date read(JsonReader in) throws IOException {
	        if (in.peek() == JsonToken.NULL) {
	            in.nextNull();
	            return null;
	        }
	        return deserializeToDate(in.nextString());
	    }

	    private synchronized Date deserializeToDate(String json) {
	        try {
	            return localFormat.parse(json);
	        } catch (ParseException ignored) {
	        }
	        try {
	            return enUsFormat.parse(json);
	        } catch (ParseException ignored) {
	        }
	        try {
	            return iso8601Format.parse(json);
	        } catch (ParseException ignored) {
	        }
	        try {
	            return shortFormat.parse(json);
	        } catch (ParseException e) {
	            return new Date(0);
	        }
	    }

	    @Override
	    public synchronized void write(JsonWriter out, Date value) throws IOException {
	        if (value == null) {
	            out.nullValue();
	            return;
	        }

	        String dateFormatAsString = iso8601Format.format(value);
	        out.value(dateFormatAsString);
	    }

	}