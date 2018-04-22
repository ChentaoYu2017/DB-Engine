import java.io.File;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XQueryCustomizedVisitor extends XQueryBaseVisitor<ArrayList<Node>> {
    private Document inputDocument = null;
    Document outputDocument = null;
    private HashMap<String, ArrayList<Node>> contextMap = new HashMap<>();
    private Stack<HashMap<String, ArrayList<Node>>> contextStack = new Stack<>();
    private ArrayList<Node> curr = new ArrayList<>();
    private boolean hasAttribute = false;

    @Override
    public ArrayList<Node> visitXqAp(XQueryParser.XqApContext context) {
        return visit(context.ap());
    }

    @Override
    public ArrayList<Node> visitXqConstructor(XQueryParser.XqConstructorContext context) {
        if (outputDocument == null){
            try {
                DocumentBuilderFactory docBF = DocumentBuilderFactory.newInstance();
                DocumentBuilder docB = docBF.newDocumentBuilder();
                outputDocument = docB.newDocument();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
        }
        ArrayList<Node> result = new ArrayList<>();

        ArrayList<Node> xqRes = visit(context.xq());

        System.out.println("xqRes size: " + xqRes.size());
        for (Node n : xqRes) {
            System.out.println(n.getNodeName());
        }

        result.add(createNode(context.NAME(0).getText(), xqRes));

        System.out.println("result size: " + result.size());

        return result;
    }

    private void permute(XQueryParser.FLWRContext ctx, int k, ArrayList<Node> results){
        int numFor;
        numFor = ctx.forClause().var().size();
        if (k == numFor){
            if (ctx.letClause() != null) visit(ctx.letClause());
            if (ctx.whereClause() != null)
                if (visit(ctx.whereClause()).size() == 0) return;
            ArrayList<Node> result = visit(ctx.returnClause());
            results.addAll(result);
        }
        else{
            String key = ctx.forClause().var(k).getText();
            ArrayList<Node> valueList = visit(ctx.forClause().xq(k));
            for (Node node: valueList){
                HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
                contextStack.push(contextMapOld);
                ArrayList<Node> value = new ArrayList<>();
                value.add(node);
                contextMap.put(key, value);
                permute(ctx, k+1, results);
                contextMap = contextStack.pop();
            }
        }


    }

    @Override
    public ArrayList<Node> visitFLWR(XQueryParser.FLWRContext context) {
        ArrayList<Node> result = new ArrayList<>();
        HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
        contextStack.push(contextMapOld);
        permute(context, 0, result);
        contextMap = contextStack.pop();
        return result;
    }

    @Override
    public ArrayList<Node> visitTwoXq(XQueryParser.TwoXqContext context) {
        ArrayList<Node> result = visit(context.xq(0));
        result.addAll(visit(context.xq(1)));
        return result;
    }

    @Override
    public ArrayList<Node> visitVariable(XQueryParser.VariableContext context) {
        System.out.println("context.getText(): " + context.getText());
        return contextMap.get(context.getText());
    }

    @Override
    public ArrayList<Node> visitXqRpall(XQueryParser.XqRpallContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        ArrayList<Node> temp = visit(context.xq());
        result.addAll(temp);
        ll.addAll(temp);
        while(!ll.isEmpty()){
            Node tempNode = ll.poll();
            result.addAll(children(tempNode));
            ll.addAll(children(tempNode));
        }
        curr = result;
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitXqwithP(XQueryParser.XqwithPContext context) {
        return visit(context.xq());
    }

    @Override
    public ArrayList<Node> visitStringC(XQueryParser.StringCContext context) {
        Node temp = createText(context.StringConstant().getText().substring(1, context.StringConstant().getText().length()-1));
        ArrayList<Node> result = new ArrayList<>();
        result.add(temp);
        return result;
    }

    @Override
    public ArrayList<Node> visitXqLet(XQueryParser.XqLetContext context) {
        HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
        contextStack.push(contextMapOld);
        ArrayList<Node> result = visitChildren(context);
        contextMap = contextStack.pop();
        return result;
    }

    @Override
    public ArrayList<Node> visitXqRp(XQueryParser.XqRpContext context) {
        curr = visit(context.xq());
        return visit(context.rp());
    }

    private ArrayList<Node> getItems (int v, XQueryParser.ForClauseContext context) {
        ArrayList<Node> result = new ArrayList<>();
        ArrayList<Node> tempList = visit(context.xq(v));
        if(context.xq().size() == 1) {
            for(Node temp: tempList) {
                ArrayList<Node> tempList2 = new ArrayList<>();
                tempList2.add(temp);
                contextMap.put(context.var(v).getText(), tempList2);
                result.add(temp);
            }
            return result;
        }
        else {
            for(Node temp: tempList) {
                HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
                contextStack.push(contextMapOld);
                ArrayList<Node> tempList2 = new ArrayList<>();
                tempList2.add(temp);
                contextMap.put(context.var(v).getText(), tempList2);
                result.addAll(getItems(v + 1, context));
                contextMap = contextStack.pop();
            }
            return result;
        }
    }

    @Override
    public ArrayList<Node> visitForClause(XQueryParser.ForClauseContext context) {
//        ArrayList<Node> result = new ArrayList<>();
//        result.addAll(getItems(0, context));
//        return result;
        return null;
    }

    @Override
    public ArrayList<Node> visitLetClause(XQueryParser.LetClauseContext context) {
        for (int i = 0; i < context.var().size(); i++) {
            contextMap.put(context.var(i).getText(), visit(context.xq(i)));
        }
        return null;
    }

    @Override
    public ArrayList<Node> visitWhereClause(XQueryParser.WhereClauseContext context) {
        return visit(context.cond());
    }

    @Override
    public ArrayList<Node> visitReturnClause(XQueryParser.ReturnClauseContext context) {
        return visit(context.xq());
    }

    @Override
    public ArrayList<Node> visitXqEqual(XQueryParser.XqEqualContext context) {

        ArrayList<Node> temp = new ArrayList<>(curr);
        ArrayList<Node> result1 = visit(context.xq(0));
        curr = temp;
        ArrayList<Node> result2 = visit(context.xq(1));
        curr = temp;
        if(result1 == null && result2 == null) {
            ArrayList<Node> list = new ArrayList<>();
            Node tmp = null;
            list.add(tmp);
            return list;
        }

        if(result1 == null || result2 == null)
            return new ArrayList<>();

        for (Node node0 : result1) {
            for (Node node1 : result2) {
                if (node0.isEqualNode(node1)) {
                    ArrayList<Node> list = new ArrayList<>();
                    Node tmp = null;
                    list.add(tmp);
                    return list;
                }
            }
        }

        return new ArrayList<>();

    }

    @Override
    public ArrayList<Node> visitXqEmpty(XQueryParser.XqEmptyContext context) {
        ArrayList<Node> xqResult = visit(context.xq());
        ArrayList<Node> result = new ArrayList<>();
        if (xqResult.isEmpty()){
            Node dummy = inputDocument.createElement("dummy");
            result.add(dummy);
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondOr(XQueryParser.XqCondOrContext context) {
        ArrayList<Node> left = visit(context.cond(0));
        if (!left.isEmpty()){
            return left;
        }
        ArrayList<Node> right = visit(context.cond(1));
        if (!right.isEmpty()){
            return right;
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitXqSome(XQueryParser.XqSomeContext context) {

        ArrayList<Node> result = new ArrayList<>();
        if (satisfyCondHelper(0, context)){
            Node True = inputDocument.createTextNode("true");
            result.add(True);
        }
        return result;

    }

    private boolean satisfyCondHelper(int k, XQueryParser.XqSomeContext ctx){
        int numFor = ctx.var().size();
        if (k == numFor){
            if (visit(ctx.cond()).size() == 1)
                return true;
        }
        else{
            String key = ctx.var(k).getText();
            ArrayList<Node> valueList = visit(ctx.xq(k));
            for (Node node: valueList){
                HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
                contextStack.push(contextMapOld);
                ArrayList<Node> value = new ArrayList<>();
                value.add(node);
                contextMap.put(key, value);
                if (k+1 <= numFor)
                    if (satisfyCondHelper(k+1, ctx)) {
                        contextMap = contextStack.pop();
                        return true;
                    }
                contextMap = contextStack.pop();
            }
        }
        return false;
    }

    @Override
    public ArrayList<Node> visitXqIs(XQueryParser.XqIsContext context) {
        ArrayList<Node> tempList = curr;
        ArrayList<Node> left = visit(context.xq(0));
        curr = tempList;
        ArrayList<Node> right = visit(context.xq(1));
        curr = tempList;
        ArrayList<Node> result = new ArrayList<>();
        for (Node i : left) {
            for (Node j : right) {
                if (i == j) {
                    result.add(i);
                    return result;
                }
            }
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondNot(XQueryParser.XqCondNotContext context) {
        ArrayList<Node> notList = visit(context.cond());
        ArrayList<Node> result = new ArrayList<>();
        if (notList.isEmpty()){
            Node dummy = inputDocument.createElement("dummy");
            result.add(dummy);
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondwithP(XQueryParser.XqCondwithPContext context) {
        return visit(context.cond());
    }

    @Override
    public ArrayList<Node> visitXqCondAnd(XQueryParser.XqCondAndContext context) {

        ArrayList<Node> left = new ArrayList<>(visit(context.cond(0)));
        ArrayList<Node> right = new ArrayList<>(visit(context.cond(1)));
        ArrayList<Node> result = new ArrayList<>();
        if (left.size() > 0 && right.size() > 0){
            Node True = inputDocument.createTextNode("true");
            result.add(True);
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitApChildren(XQueryParser.ApChildrenContext context) {
        return visitChildren(context);
    }

    @Override
    public ArrayList<Node> visitApAll(XQueryParser.ApAllContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        visit(context.doc());
        result.addAll(curr);
        ll.addAll(curr);
        while(!ll.isEmpty()) {
            Node temp = ll.poll();
            result.addAll(children(temp));
            ll.addAll(children(temp));
        }
        curr = result;
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitDoc(XQueryParser.DocContext context) {
        if(inputDocument == null) {
            File xmlFile = new File(context.fname().getText());
            DocumentBuilderFactory docBF = DocumentBuilderFactory.newInstance();
            docBF.setIgnoringElementContentWhitespace(true);
            DocumentBuilder docB = null;
            try {
                docB = docBF.newDocumentBuilder();
            } catch (ParserConfigurationException pE1) {
                pE1.printStackTrace();
            }

            try {
                if (docB != null) {
                    inputDocument = docB.parse(xmlFile);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            inputDocument.getDocumentElement().normalize();
        }
        ArrayList<Node> result = new ArrayList<>();
        result.add(inputDocument);
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitAllChildren(XQueryParser.AllChildrenContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for(Node temp : curr) {
            result.addAll(children(temp));
        }
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpwithP(XQueryParser.RpwithPContext context) {
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitTagName(XQueryParser.TagNameContext context) {
        ArrayList<Node> result = new ArrayList<>();
        String tName = context.getText();
        for(Node temp : curr) {
            ArrayList<Node> list = children(temp);
            for(Node i : list) {
                if(i.getNodeName().equals(tName)) result.add(i);
            }
        }
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpAll(XQueryParser.RpAllContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        visit(context.rp(0));
        result.addAll(curr);
        ll.addAll(curr);
        while(!ll.isEmpty()) {
            Node temp = ll.poll();
            result.addAll(children(temp));
            ll.addAll(children(temp));
        }
        curr = result;
        return visit(context.rp(1));
    }

    @Override
    public ArrayList<Node> visitParent(XQueryParser.ParentContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for(Node temp : curr) {
            if(!result.contains(temp.getParentNode())) result.add(temp.getParentNode());
        }
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitAttribute(XQueryParser.AttributeContext context) {
        ArrayList<Node> result = new ArrayList<>();
        hasAttribute = true;
        for (Node temp : curr) {
            Element e = (Element) temp;
            String attr = e.getAttribute(context.NAME().getText());
            if (!attr.equals("")) {
                result.add(temp);
                attr = context.NAME().getText()+"=\""+ attr +"\"";
            }
        }
        curr = result;
        return result;
    }



    @Override
    public ArrayList<Node> visitRpChildren(XQueryParser.RpChildrenContext context) {
        visit(context.rp(0));
        ArrayList<Node> result = visit(context.rp(1));
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitText(XQueryParser.TextContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for (Node n : curr) {
            ArrayList<Node> list = children(n);
            for (Node child : list) {
                if (child.getNodeType() == Node.TEXT_NODE){
                    result.add(child);
                }
            }
        }
        curr = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitCurrent(XQueryParser.CurrentContext context) {
        return curr;
    }

    @Override
    public ArrayList<Node> visitTwoRp(XQueryParser.TwoRpContext context) {
        ArrayList<Node> result1 = new ArrayList<>();
        ArrayList<Node> result2 = new ArrayList<>();
        ArrayList<Node> tempList = new ArrayList<>(curr);
        result1.addAll(visit(context.rp(0)));
        curr = tempList;
        result2.addAll(visit(context.rp(1)));
        result1.addAll(result2);

        curr = result1;
        return result1;
    }

    @Override
    public ArrayList<Node> visitRpFilter(XQueryParser.RpFilterContext context) {
        ArrayList<Node> result = visit(context.rp());
        ArrayList<Node> filter= visit(context.filter());
        if (hasAttribute) {
            curr = filter;
            hasAttribute = false;
            return filter;
        }
        else if (filter.isEmpty()) {
            return new ArrayList<>();
        }
        else return result;
//        visit(context.rp());
//        curr = visit(context.filter());
//        return curr;
    }

    @Override
    public ArrayList<Node> visitFilAnd(XQueryParser.FilAndContext context) {
        ArrayList<Node> left = visit(context.filter(0));
        ArrayList<Node> right = visit(context.filter(1));
        if (!left.isEmpty() && !right.isEmpty()) {
            return left;
        }
        else return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFilEqual(XQueryParser.FilEqualContext context) {
        ArrayList<Node> tempList = curr;
        ArrayList<Node> left = visit(context.rp(0));
        curr = tempList;
        ArrayList<Node> right = visit(context.rp(1));
        curr = tempList;
        for (Node i : left) {
            for (Node j : right) {
                if (i.isEqualNode(j)) {
                    return tempList;
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFilNot(XQueryParser.FilNotContext context) {
        ArrayList<Node> rec = new ArrayList<Node>(curr);
        ArrayList<Node> res = new ArrayList<Node>();
        for(Node n : rec){
            curr = new ArrayList<Node>();
            curr.add(n);
            ArrayList<Node> filter= visit(context.filter());
            if(filter.isEmpty())res.add(n);
        }
        curr=res;
        return curr;
    }

    @Override
    public ArrayList<Node> visitFilOr(XQueryParser.FilOrContext context) {
        ArrayList<Node> left = visit(context.filter(0));
        ArrayList<Node> right = visit(context.filter(1));
        if (!left.isEmpty() || !right.isEmpty()) {
            return curr;
        }
        else return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFilIs(XQueryParser.FilIsContext context) {
        ArrayList<Node> tempList = curr;
        ArrayList<Node> left = visit(context.rp(0));
        curr = tempList;
        ArrayList<Node> right = visit(context.rp(1));
        curr = tempList;
        for (Node i : left) {
            for (Node j : right) {
                if (i == j) {
                    return tempList;
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFilwithP(XQueryParser.FilwithPContext context) {
        return visit(context.filter());
    }

    @Override
    public ArrayList<Node> visitFilRp(XQueryParser.FilRpContext context) {
        ArrayList<Node> tempList = curr;
        ArrayList<Node> result = visit(context.rp());
        curr = tempList;
        return result;
    }

    private Node createNode(String s, ArrayList<Node> nodeList) {
        Node result = outputDocument.createElement(s);
        for (Node temp : nodeList) {
            if (temp != null) {
                Node newNode = outputDocument.importNode(temp, true);
                result.appendChild(newNode);
            }
        }
        return result;
    }

    private Node createText(String s) {
        return inputDocument.createTextNode(s);
    }

    private static ArrayList<Node> children(Node n){
        ArrayList<Node> childrenList = new ArrayList<>();
        for(int i = 0; i < n.getChildNodes().getLength(); ++i){
            childrenList.add(n.getChildNodes().item(i));
        }
        return childrenList;
    }
}
