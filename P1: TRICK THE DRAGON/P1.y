%{
   #include<bits/stdc++.h>
    using namespace std;
    void yyerror(char *);
    int yylex(void);
    struct Macro
    {
        string macroBody;
        vector<string>parameters;
        bool statement;
    };
    unordered_map<string,Macro>macros;
   
    
    //helper functions
    void storeMacro(vector<string>*paramList,const string&macroName,const char* body,bool _statement);
    string replaceIdentifiers(const string& body,const string&identifier,const string&replacement,bool expr_flag);
    char* expandMacroCall_stmt(const string&macroName,const vector<string>arguments);
    char* expandMacroCall_expr(const string&macroName,const vector<string>arguments);
    char* concatCharPointer(const char*cPtr1,const char*cPtr2);

    extern "C" {
    extern int yydebug;
}
    // true = last non-space char of s is '}' or ';'
    static bool expansion_ends_with_semicolon_or_brace(const char *s) {
    if (!s) return false;
    int n = (int)strlen(s);
    while (n > 0 && isspace((unsigned char)s[n-1])) --n;
    if (n == 0) return false;
    return s[n-1] == '}' || s[n-1] == ';';
}
     unordered_map<string,bool>is_stmt;//true for macro_stmt

%}

%debug

%code requires {
    #include <vector>
    #include <string>
    typedef std::vector<std::string>* ParamListPtr;
}

%union {
    char* val;
    int valid;
    int value;
    //vector<string>*parameterListPtr;
    ParamListPtr parameterListPtr;
}

%token <val> IDENTIFIER
%token <value> NUMBER /*integer value*/
%token<val>LAMBDA_FRONT
%token INT/*keyword int*/ BOOLEAN //INT_ARRAY //can match int array in a production ,instead of creating a new token
%token IF ELSE DO WHILE RETURN
%token CLASS PUBLIC STATIC VOID MAIN STRING 
%token SYSTEM OUT PRINTLN EXTENDS 
%token TRUE FALSE THIS NEW //NEW_INT //new token , check
%token HASH_DEFINE DOT LENGTH
%token LOGICAL_AND LOGICAL_OR NEQ LEQ LOGICAL_NOT EQUAL_TO
%token LEFT_PAREN RIGHT_PAREN LEFT_BRACE RIGHT_BRACE LEFT_BRACKET RIGHT_BRACKET SEMICOLON COMMA 
%token IMPORT UTIL FUNCTION 

//%type <valid> expression 

%left PLUS MINUS 
%left MUL DIV  //  left_paren* and / have higher precedence that + and -right_paren
/* %right EXP */
%left NEQ LEQ '<' '>'
%left LOGICAL_OR
%left LOGICAL_AND
%right LOGICAL_NOT



//if rules return something ,mark them as %type 
%type<val>class left_brace right_brace public static void main String System dot out println semicolon comma left_paren right_paren left_bracket right_bracket extends
%type<val>if else do while true false this new  logical_and logical_not logical_or int neq leq equal_to boolean plus minus div mul length hash_define return 
%type <val> Identifier Integer Expression Statement MacroDefinition MacroDefExpression MacroDefStatement
%type <val> multi_Statement multi_Comma_Expression multi_MethodDeclaration  multi_Comma_Type_Identifier
%type <val> multi_Expression multi_Type_Identifier_semicolon multi_TypeDeclaration
%type <val> MainClass TypeDeclaration MethodDeclaration Goal
%type <val> PrimaryExpression Type optional_importstmt lambda_front

%type<parameterListPtr>multi_Comma_Identifier

%%
//Grammar

Goal :optional_importstmt multi_MacroDefinition MainClass multi_TypeDeclaration{

            //char* ptr1=concatCharPointer();
            char* ptr1=concatCharPointer($1,$3);
            char* finalPtr=concatCharPointer(ptr1,$4);
            printf("%s\n",finalPtr);
            //free($1);
            free($1);
            free($3);
            free($4);
            free(ptr1);
            free(finalPtr);
            $$=strdup("");
        }


MainClass:
    class Identifier left_brace public static void main left_paren String left_bracket right_bracket Identifier right_paren left_brace System dot out dot println left_paren  Expression right_paren semicolon right_brace right_brace
    {string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7)+string($8)+string($9)+string($10)+string($11)+string($12)+string($13)+string($14)+string($15)+string($16)+string($17)+string($18)+string($19)+string($20)+string($21)+string($22)+string($23)+string($24)+string($25));$$=strdup(cur.c_str());}

TypeDeclaration	:
	class  Identifier left_brace multi_Type_Identifier_semicolon  multi_MethodDeclaration right_brace {string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6));$$=strdup(cur.c_str());}
    |class Identifier extends Identifier left_brace multi_Type_Identifier_semicolon  multi_MethodDeclaration right_brace{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7)+string($8));$$=strdup(cur.c_str());}


MethodDeclaration:
	public Type Identifier left_paren Type Identifier multi_Comma_Type_Identifier right_paren left_brace multi_Type_Identifier_semicolon multi_Statement return Expression semicolon right_brace
    {string cur=(string($1)+string($2)+string(" ")+string($3)+string($4)+string($5)+string(" ")+string($6)+string($7)+string($8)+string($9)+string($10)+string($11)+string($12)+string($13)+string($14)+string($15));$$=strdup(cur.c_str());}
    |public Type Identifier left_paren right_paren left_brace multi_Type_Identifier_semicolon multi_Statement return Expression semicolon right_brace
    {string cur=(string($1)+string($2)+string(" ")+string($3)+string($4)+string($5)+string($6)+string($7)+string($8)+string($9)+string($10)+string($11)+string($12));$$=strdup(cur.c_str());}


Type:
	int left_bracket right_bracket {string cur=(string($1)+string($2)+string($3));$$=strdup(cur.c_str());}
    |boolean{$$=$1;}
    |int{$$=$1;}
    |Identifier{$$=$1;}
    |FUNCTION '<' Identifier comma Identifier '>'{string cur="Function < "+string($3)+" , "+string($5)+string(" >");$$=strdup(cur.c_str());}

Statement	:
	left_brace multi_Statement right_brace {string cur=(string($1)+string($2)+string($3));$$=strdup(cur.c_str());}
    |System dot out dot println left_paren Expression right_paren semicolon{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7)+string($8)+string($9));$$=strdup(cur.c_str());}
    |Identifier  equal_to Expression semicolon{string cur=(string($1)+string($2)+string($3)+string($4));$$=strdup(cur.c_str());}
    |Identifier left_bracket Expression right_bracket equal_to Expression semicolon{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7));$$=strdup(cur.c_str());}
    |if left_paren Expression right_paren Statement{string cur=(string($1)+string($2)+string($3)+string($4)+string($5));$$=strdup(cur.c_str());}
    |if left_paren Expression right_paren Statement else Statement{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7));$$=strdup(cur.c_str());}
    |do Statement while left_paren Expression right_paren semicolon{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7));$$=strdup(cur.c_str());}
    |while left_paren Expression right_paren Statement{string cur=(string($1)+string($2)+string($3)+string($4)+string($5));$$=strdup(cur.c_str());}
    |Identifier left_paren right_paren/* Macro stmt call */      /*2) replace macros*/
    {
        vector<string> emptyv;
        char* expanded = expandMacroCall_stmt(string($1 ? $1 : ""), emptyv);

        bool endsr = expansion_ends_with_semicolon_or_brace(expanded);

     if (endsr) {
            /* expanded already ends with ';' or '}', so keep it same */
            $$ = expanded;  
        } else {
            /* add a trailing semicolon */
            char* tail = strdup(";\n");
            char* stmt = concatCharPointer(expanded, tail);
            free(expanded);
            free(tail);
            $$ = stmt;
        }

        free($1);  
    }
    |Identifier left_paren Expression multi_Comma_Expression right_paren semicolon /* Macro stmt call */
    {
         
    string combined = string($3 ? $3 : "");
    if ($4 && strlen($4) > 0) combined += string($4);  

    
    vector<string> argvec;
    string cur; int depth = 0;
    for (size_t i = 0; i < combined.size(); ++i) {
        char ch = combined[i];
        if (ch == '(') { depth++; cur.push_back(ch); }
        else if (ch == ')') { depth--; cur.push_back(ch); }
        else if (ch == ',' && depth == 0) {
            size_t L = cur.find_first_not_of(" \t\r\n");
            size_t R = cur.find_last_not_of(" \t\r\n");
            if (L == string::npos) argvec.push_back("");
            else argvec.push_back(cur.substr(L, R-L+1));
            cur.clear();
        } else cur.push_back(ch);
    }
    if (!cur.empty()) {
        size_t L = cur.find_first_not_of(" \t\r\n");
        size_t R = cur.find_last_not_of(" \t\r\n");
        if (L == string::npos) argvec.push_back("");
        else argvec.push_back(cur.substr(L, R-L+1));
    }

    /* Expand macro */
    char* expanded = expandMacroCall_stmt(string($1 ? $1 : ""), argvec);

    bool endsr = expansion_ends_with_semicolon_or_brace(expanded);

        if (endsr) {
            
            $$ = expanded;
            free($6);         
        } else {
           
            char* stmt = concatCharPointer(expanded, $6);
            free(expanded);
            free($6);
            $$ = stmt;
        }

        free($1);
        if ($3) free($3);
        if ($4) free($4);
    }


Expression	:
	PrimaryExpression logical_and PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression logical_or PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression neq PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression leq PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression plus PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression minus PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression mul PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression div PrimaryExpression{string cur=("("+string($1)+string($2)+string($3)+")");$$=strdup(cur.c_str());}
    |PrimaryExpression left_bracket PrimaryExpression right_bracket{string cur=(string($1)+string($2)+string($3)+string($4));$$=strdup(cur.c_str());}
    |PrimaryExpression dot length{string cur=(string($1)+string($2)+string($3));$$=strdup(cur.c_str());}
    |PrimaryExpression{$$=$1;}//added expression
    |PrimaryExpression dot Identifier left_paren Expression multi_Comma_Expression right_paren{string cur=(string($1)+string($2)+string($3)+string($4)+string($5)+string($6)+string($7));$$=strdup(cur.c_str());}
    |PrimaryExpression dot Identifier left_paren  right_paren{string cur=(string($1)+string($2)+string($3)+string($4)+string($5));$$=strdup(cur.c_str());}
    |Identifier left_paren Expression multi_Comma_Expression right_paren/* Macro expr call */
    {
        string combined=string($3 ? $3:"");
        if ($4 && strlen($4) > 0) 
        {
            combined += string($4);
        }

        //**implement replacing step using helper functions**
         vector<string> argvec;
        string cur; int depth = 0;
        for (size_t i=0;i<combined.size();++i) 
        {
            char ch = combined[i];
            if (ch == '(') { depth++; cur.push_back(ch); }
            else if (ch == ')') { depth--; cur.push_back(ch); }
            else if (ch == ',' && depth == 0) 
            {
                size_t L = cur.find_first_not_of(" \t\r\n");
                size_t R = cur.find_last_not_of(" \t\r\n");
                if (L==string::npos) argvec.push_back("");
                else argvec.push_back(cur.substr(L, R-L+1));
                cur.clear();
            } 
            else cur.push_back(ch);
        }
        if (!cur.empty()) 
        {
            size_t L = cur.find_first_not_of(" \t\r\n");
            size_t R = cur.find_last_not_of(" \t\r\n");
            if (L==string::npos) argvec.push_back("");
            else argvec.push_back(cur.substr(L, R-L+1));
        }

        char* expanded =expandMacroCall_expr(string($1 ? $1:""), argvec);
        free($1); free($3); if ($4) free($4);
        $$ = expanded; /* caller will free or propagate */
    }
    |Identifier left_paren right_paren/* Macro expr call */  
    {
        vector<string> emptyv;
        char* expanded = expandMacroCall_expr(string($1 ? $1:""), emptyv);
        free($1);
        $$ = expanded;
    }
    |lambda_front Expression{string cur=(string($1)+string($2));$$=strdup(cur.c_str());}//try to make this more tight


PrimaryExpression	:   
     Integer{$$=$1;}
    |true{$$=$1;}
    |false{$$=$1;}
    |Identifier{$$=$1;}
    |this{$$=$1;}
    |new int left_bracket Expression right_bracket{string cur=(string($1)+string($2)+string($3)+string($4)+string($5));$$=strdup(cur.c_str());}
    |new Identifier left_paren right_paren{string cur=(string($1)+string($2)+string($3)+string($4));$$=strdup(cur.c_str());}
    |logical_not Expression{string cur=(string($1)+string($2));$$=strdup(cur.c_str());}
    |left_paren Expression right_paren{string cur=(string($1)+string($2)+string($3));$$=strdup(cur.c_str());}


MacroDefinition	:	
     MacroDefExpression{$$=$1;}
    | MacroDefStatement{$$=$1;}

//1)store macro names ,parameters ,body
  //2) replace macros
  //3) move the final strings up and concatenate at the end

MacroDefStatement :
    hash_define Identifier left_paren Identifier  multi_Comma_Identifier right_paren left_brace multi_Statement right_brace
     {
        is_stmt[string($2)]=true;
        std::vector<std::string>* params = new std::vector<std::string>();
        params->push_back(std::string($4));   // first param 

        if ($5) {
            for (const auto &s : *$5) params->push_back(s);
            delete $5; //
        }

       
        storeMacro(params,std::string($2), $8, true);

        
        free($2);   // macro name 
        free($4);   // first param 
        free($8);   // body

        $$ = strdup("");  
    }
    |hash_define Identifier left_paren  right_paren left_brace multi_Statement right_brace
    {
        is_stmt[string($2)]=true;
        storeMacro( new vector<string>(),string($2), $6, true);
        free($2);
        free($6);
        $$ = strdup("");
    }

MacroDefExpression	:
    hash_define Identifier  left_paren Identifier  multi_Comma_Identifier right_paren left_paren multi_Expression right_paren
    {
        is_stmt[string($2)]=false;
        std::vector<std::string>* params = new std::vector<std::string>();
        params->push_back(std::string($4));   //store first param

        if ($5) {
            for (const auto &s : *$5) params->push_back(s);
            delete $5; //
        }

       
        storeMacro( params,std::string($2),$8, false);

        
        free($2);   // macro name 
        free($4);   // first 
        free($8);   // body (multi_Statement)

        $$ = strdup("");    
    }
    |hash_define Identifier  left_paren  right_paren left_paren multi_Expression right_paren
    {
        is_stmt[string($2)]=false;
        storeMacro( new vector<string>(),string($2),$6, false);
        free($2);
        free($6);
        $$ = strdup("");
    }

Identifier	:
	IDENTIFIER {string cur=string($1);$$=strdup(cur.c_str());}

Integer	:
    NUMBER {
            char buf[32];
            sprintf(buf, "%d", $1);
            $$ = strdup(buf);
        }

//multi utilities
multi_Type_Identifier_semicolon:{ $$ = strdup(""); }
    | multi_Type_Identifier_semicolon Type Identifier semicolon{
        string cur=string($1)+string($2)+string(" ")+string($3)+string($4);//easier
        // char* p1 = concatCharPointer($1, $2); free($1); free($2);
        // char* p2 = concatCharPointer(p1, $3); free(p1); free($3);
        // char* rest = $4;
        // char* t = concatCharPointer(p2, rest);
        // free(p2); free(rest);
        $$ = strdup(cur.c_str());
    }

multi_Comma_Type_Identifier:{ $$ = strdup(""); }
    | multi_Comma_Type_Identifier comma Type Identifier  {
        char* tmp1 = concatCharPointer($1, $2);
        free($1); free($2);
        char* tmp2 = concatCharPointer(tmp1, $3);
        free(tmp1); free($3);
        char* tmp3=strdup((string(tmp2)+string(" ")).c_str());
        free(tmp2);
        char* result=concatCharPointer(tmp3, $4);
        free(tmp3);free($4);
        $$ = result;
    }

multi_Statement:{$$=strdup("");}
    | Statement multi_Statement{
        char*t=concatCharPointer($1,$2);
        free($1);
        free($2);
        $$=t;
    }

multi_Expression:{$$=strdup("");}
    |  multi_Expression Expression{
        char*t=concatCharPointer($1,$2);
        free($1);
        free($2);
        $$=t;
    }

multi_MethodDeclaration:{ $$ = strdup(""); }
    | multi_MethodDeclaration MethodDeclaration{
        char*t=concatCharPointer($1,$2);
        free($1);
        free($2);
        $$=t;
    }

multi_TypeDeclaration :{ $$ = strdup(""); }
    |multi_TypeDeclaration  TypeDeclaration {
        char*t=concatCharPointer($1,$2);
        free($1);
        free($2);
        $$=t;
    }

multi_MacroDefinition:       /*no need to return as we are not printing */
    | multi_MacroDefinition MacroDefinition

multi_Comma_Expression:{ $$ = strdup(""); }
    | multi_Comma_Expression comma Expression{
        char* tmp1 = concatCharPointer($1, $2);
        free($1); free($2);
        char* result = concatCharPointer(tmp1, $3);
        free(tmp1); free($3);
        $$ = result;
    }

multi_Comma_Identifier: {$$=new vector<string>();}
    | multi_Comma_Identifier comma Identifier{$$=$1;$$->push_back(string($3));free($3);}


//printing statements

optional_importstmt:{$$=strdup("");}
    |IMPORT UTIL SEMICOLON {$$=strdup("import java.util.function.Function;\n");}

lambda_front:
    LAMBDA_FRONT {string cur=string($1);$$=strdup(cur.c_str());}

class :
    CLASS {$$=strdup(" class ");}

left_brace:
    LEFT_BRACE {$$=strdup("{\n");}

right_brace:
    RIGHT_BRACE {$$=strdup("}\n");}

left_bracket:
    LEFT_BRACKET {$$=strdup(" [ ");}

right_bracket:
    RIGHT_BRACKET {$$=strdup(" ] ");}

left_paren:
    LEFT_PAREN {$$=strdup(" ( ");}

right_paren:
    RIGHT_PAREN {$$=strdup(" ) ");}

public:
    PUBLIC {$$=strdup(" public ");}

static:
    STATIC {$$=strdup(" static ");}

void:
    VOID {$$=strdup(" void ");}

main:
    MAIN {$$=strdup(" main ");}

String:
    STRING {$$=strdup(" String ");}

System:
    SYSTEM {$$=strdup("System");}

dot:
    DOT {$$=strdup(".");}

out:
    OUT {$$=strdup("out");}
 
println:
    PRINTLN {$$=strdup("println");}

semicolon:
    SEMICOLON {$$=strdup(";\n");}

extends:
    EXTENDS{$$=strdup(" extends ");}
if:
    IF {$$=strdup(" if ");}
else:
    ELSE {$$=strdup(" else ");}
do:
    DO{$$=strdup(" do ");}
while:
    WHILE{$$=strdup(" while ");}
true:
    TRUE {$$=strdup(" true ");}
false:
    FALSE{$$=strdup(" false ");}
this:
    THIS{$$=strdup(" this ");}
new:
    NEW {$$=strdup(" new ");}
logical_not:
    LOGICAL_NOT{$$=strdup("!");}
logical_and:
    LOGICAL_AND{$$=strdup("&&");}
logical_or:
    LOGICAL_OR{$$=strdup("||");}
int:
    INT {$$=strdup(" int ");}
boolean:
    BOOLEAN{$$=strdup(" boolean ");}
neq:
    NEQ{$$=strdup("!= ");}
leq:
    LEQ{$$=strdup("<= ");}
plus:
    PLUS{$$=strdup(" + ");}
minus:
    MINUS{$$=strdup(" - ");}
mul:
    MUL {$$=strdup(" * ");}
div:
    DIV{$$=strdup(" / ");}
equal_to:
    EQUAL_TO {$$=strdup("=");}
length:
    LENGTH{$$=strdup(" length ");}

return :
    RETURN {$$=strdup(" return ");}

hash_define:
    HASH_DEFINE {$$=strdup(" #define ");}


comma:
    COMMA {$$=strdup(" , ");}


%%


//function implementations

//utility function to concatenate two c_strings
  char* concatCharPointer(const char*cPtr1,const char*cPtr2)
  {
        if(cPtr1==NULL) cPtr1=strdup("");
        if(cPtr2==NULL) cPtr2=strdup("");
        string concatenation=string(cPtr1)+string(cPtr2);
        char* result=strdup(concatenation.c_str());//allocate new memory and return the pointer to it
        return result;
  }

    //replace arguments with placeholders to avoid ambiguity
    string replace_with_placeholders(const string &body, const vector<string> &params) {
            string res = body;
    vector<pair<string,int>> sort_param;
    for (int i = 0; i < params.size(); i++) {
        sort_param.push_back({params[i], (int)i});
    }
    sort(sort_param.begin(), sort_param.end(), [&](auto &p1, auto &p2){
        if(p1.first.size()==p2.first.size()) return p1.second<p2.second;
          return p1.first.size() > p2.first.size();
    });

    for (auto &p : sort_param) {
        string placeholder = "__MANOJ_PARAM_RANDOM_" + to_string(p.second) + "__";
        res = replaceIdentifiers(res, p.first, placeholder, false);
    }
    return res;
    }

  void storeMacro(vector<string>*param_list_ptr,const string&macroName,const char* body,bool is_stmt)
  {
        Macro currentMacro;
        if(param_list_ptr)
        {
            currentMacro.parameters=*param_list_ptr;
            delete param_list_ptr;
        }
        else
        {
            currentMacro.parameters.clear();
        }
        currentMacro.macroBody=((body!=NULL)?replace_with_placeholders(string(body),currentMacro.parameters):string());
        currentMacro.statement=is_stmt;
        macros[macroName]=std::move(currentMacro);//transfer the resource
  }

  string replace_arguments(const Macro &macro, const vector<string> &arguments) {
     string res = macro.macroBody;

    vector<pair<string,int>> sort_param;
    for (int i = 0; i < macro.parameters.size(); i++) {
        sort_param.push_back({macro.parameters[i], (int)i});
    }
    sort(sort_param.begin(), sort_param.end(), [&](auto &p1, auto &p2){
        if(p1.first.size()==p2.first.size()) return p1.second<p2.second;
        return p1.first.size() > p2.first.size();
    });

    for (auto &p : sort_param) {
        int i = p.second;
        string placeholder = "__MANOJ_PARAM_RANDOM_" + to_string(i) + "__";
        string replacement = (i < arguments.size() ? arguments[i] : "");
        res = replaceIdentifiers(res, placeholder, replacement, !macro.statement);
    }
    return res;
}

  string replaceIdentifiers(const string& body,const string&identifier,const string&replacement,bool expr_flag)
  {
    string res;
    int bodyLength=body.length();
    int identifierLength=identifier.length();
    int i=0;
    while(i<bodyLength)
    {
        bool match=false;
        if(i+identifierLength<=bodyLength)
        {
            if(body.substr(i,identifierLength)==identifier)
            {
                //check if it's the whole identifier or not
                bool left = ((i==0) || !((body[i-1]>='0'&&body[i-1]<='9') || body[i-1]=='_'));
                bool right = (i+identifierLength==bodyLength) || !((body[i+identifierLength]>='0'&&body[i+identifierLength]<='9')|| body[i+identifierLength]=='_');
                if (left && right) match = true;
            }
        }
        //replace it with replacement
        if(match)
        {
            res+=replacement;
            i+=identifierLength;
        }
        else{
            res+=body[i];
            i++;
        }
    }
    return res;
  }
  //called by grammar
  char* expandMacroCall_stmt(const string&macroName,const vector<string>arguments)
  {
        if(!macros.count(macroName))
        {
            //not a macro
            string call = macroName + string("(");
            for (int i=0;i<(int)arguments.size();++i) 
            {
                if (i) call.push_back(',');
                call += arguments[i];
            }
            call.push_back(')');
            return strdup(call.c_str());
        }
        //INVALID
        if(!is_stmt[macroName])
        {
            yyerror("");
        }
        Macro curMacro=macros[macroName];
        //invalid
        if(curMacro.parameters.size()!=arguments.size())
        {
            yyerror("");
        }
        //substitute
        string finalStr=replace_arguments(curMacro,arguments);
        if(!curMacro.statement) 
        {
            finalStr="("+finalStr+")";
        }
        return strdup(finalStr.c_str());
  }
  char* expandMacroCall_expr(const string&macroName,const vector<string>arguments)
  {
        //check map
        if(!macros.count(macroName))
        {
            //not a macro
            string call = macroName + string("(");
            for (int i=0;i<(int)arguments.size();++i) 
            {
                if (i) call.push_back(',');
                call += arguments[i];
            }
            call.push_back(')');
            return strdup(call.c_str());
        }
        //INVALID
         if(is_stmt[macroName])
        {
            yyerror("");
        }
        Macro curMacro=macros[macroName];
        //invalid
        if(curMacro.parameters.size()!=arguments.size())
        {
            yyerror("");
        }
        //substitute
        string finalStr=replace_arguments(curMacro,arguments);
        if(!curMacro.statement) 
        {
            finalStr="("+finalStr+")";
        }
        return strdup(finalStr.c_str());
  }


void yyerror(char *s) {
    printf("// Failed to parse macrojava code.\n");
    exit(1);
}

int main(int argc, char* argv[]) {
    //yydebug = 1; 
    return yyparse();
}