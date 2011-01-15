package clearcommon.treebank;

public class OntoNoteTreeFileResolver implements TreeFileResolver {

    public OntoNoteTreeFileResolver()
    {
    }
    
    @Override
    public String resolve(String fileName, String input) {
        return input.substring(0,input.indexOf("@"))+fileName.substring(fileName.lastIndexOf('.'), fileName.length()-4)+"parse";
    }

}
