

package arc.net;

@SuppressWarnings("serial")
public class ArcNetException extends RuntimeException{
    public ArcNetException(){
        super();
    }

    public ArcNetException(String message, Throwable cause){
        super(message, cause);
    }

    public ArcNetException(String message){
        super(message);
    }

    public ArcNetException(Throwable cause){
        super(cause);
    }
}
