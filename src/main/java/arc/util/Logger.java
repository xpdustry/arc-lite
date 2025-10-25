package arc.util;

import arc.util.Log.*;


/** Logger with topics. */
public class Logger{
    protected static final Object[] empty = {};
    
    public static LoggerHandler defaultLogger = new DefaultLoggerHandler();
    public static TopicFormatter defaultTopicFormatter = new DefaultTopicFormatter();
    
    /** Custom logging level. {@code null} to use {@link Log#level}. */
    public LogLevel level;
    /** Commonly the first topic is a main and second is a thing related. {@code null} if no topics. */
    public String[] topics;
    public LoggerHandler logger = defaultLogger;
    public TopicFormatter topicFormatter = defaultTopicFormatter;
    /** {@code null} to use {@link Log#formatter}. */
    public LogFormatter formatter;

    public Logger(){ 
        topics = null; 
    }
    
    /** Uses the capitalized class name (without package) as a topic. */
    public Logger(Class<?> clazz){ 
        this(Strings.insertSpaces(clazz.getSimpleName())); 
    }
    
    /** 
     * Uses the capitalized class name (without package) for the first class, 
     * and the fully qualified class name for others. 
     */
    public Logger(Class<?>... classes){
        if(classes == null || classes.length == 0){
          topics = null;
          return;
        }
        topics = new String[classes.length];
        topics[0] = Strings.insertSpaces(classes[0].getSimpleName());
        for(int i = 1; i < classes.length; i++)
            topics[i] = classes[i].getName();
    }

    public Logger(String topic){
        topic = topic.trim();
        topics = topic.isEmpty() ? null : new String[] {topic};
    }

    public Logger(String... topics){
        this.topics = topics == null || topics.length == 0 ? null : topics;
    }

    public void log(LogLevel level, String text, Throwable th, Object... args){
        if((level != null ? level.ordinal() : Log.level.ordinal()) > level.ordinal()) return;
        logger.log(this, level, text, th, args);
    }
    public void log(LogLevel level, String text, Object... args){ log(level, text, null, args); }
    public void log(LogLevel level, String text){ log(level, text, empty); }

    public void debug(String text, Object... args){ log(LogLevel.debug, text, args); }
    public void debug(String text){ debug(text, empty); }
    public void debug(Object object){ debug(String.valueOf(object), empty); }

    public void info(String text, Object... args){ log(LogLevel.info, text, args); }
    public void info(String text){ info(text, empty); }
    public void info(Object object){ info(String.valueOf(object), empty); }
    
    public void warn(String text, Object... args){ log(LogLevel.warn, text, args); }
    public void warn(String text){ warn(text, empty); }
    public void warn(Object object){ warn(String.valueOf(object), empty); }

    public void err(String text, Throwable th, Object... args){ log(LogLevel.err, text, th, args); }
    public void err(String text, Object... args){ err(text, null, args); }
    public void err(String text, Throwable th){ err(text, th, empty); }
    public void err(String text){ err(text, null, empty); }
    public void err(Object object){ err(String.valueOf(object), null, empty); }
    public void err(Throwable th){ err(null, th, empty); }

    /** Log an empty "info" line. */
    public void ln(){ log(LogLevel.info, null, empty); }

    
    public static interface LoggerHandler{
        /** Text and topics colors must be parsed by this method. */
        void log(Logger context, LogLevel level, String text, Throwable th, Object... args);
    }

    public static interface TopicFormatter{
        String format(Logger context, LogLevel level, String[] topics);
    }

    
    public static class DefaultLoggerHandler implements LoggerHandler{
        public LogHandler delegate;

        public DefaultLoggerHandler(){ 
            this(Log.logger); 
        }

        public DefaultLoggerHandler(LogHandler delegate){ 
            this.delegate = delegate; 
        }

        public String format(Logger context, String text, Object... args){
            return context == null || context.formatter == null ? Log.format(text, args) :
                   context.formatter.format(text, Log.useColors, args);
        }
        
        @Override
        public void log(Logger context, LogLevel level, String text, Throwable th, Object... args){
            if(text != null){
                text = format(context, text, args);
                if(th != null) text += ": " + Strings.getStackTrace(th);
            }else if(th != null) text = Strings.getStackTrace(th);
            
            synchronized(delegate){
                String tag = context == null || context.topics == null || context.topicFormatter == null ? "" : 
                    format(context, context.topicFormatter.format(context, level, context.topics), empty);
                
                if(text == null || text.isEmpty()){ 
                    delegate.log(level, tag); 
                    return; 
                }
                
                int i = 0, nl;
                while((nl = text.indexOf('\n', i)) != -1){
                    delegate.log(level, tag + text.substring(i, nl));
                    i = nl + 1;
                }
                delegate.log(level, tag + (i == 0 ? text : text.substring(i)));
            }
        }
    }
    
    public static class DefaultTopicFormatter implements TopicFormatter{
        public static final String[] tags = {"&lc&fb", "&lb&fb", "&ly&fb", "&lr&fb", ""};
        //TODO: cache builded topic?
        
        @Override
        public String format(Logger context, LogLevel level, String[] topics){ 
            StringBuilder builder = new StringBuilder();
            for (String topic : topics){
               builder.append(tags[level.ordinal()]).append('[').append(topic).append("]&fr "); 
            }
            return builder.toString(); 
        }
    }
}
