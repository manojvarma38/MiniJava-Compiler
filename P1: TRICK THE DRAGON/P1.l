%{
    #include<bits/stdc++.h>
     #include "P1.tab.h"
    void yyerror(char *);
    using namespace std;

%}


%%

"//"[^\n]*      {/*single line comment*/}
"("[ \t]*[a-zA-Z_][a-zA-Z0-9_]*[ \t]*")"[ \t]*"->"  { yylval.val= strdup(yytext);return LAMBDA_FRONT;}

"class"         {return CLASS;}
"public"        {return PUBLIC;}
"static"        {return STATIC;}
"void"          {return VOID;}
"main"          {return MAIN; }
"String"        {return STRING;}
"("             { return LEFT_PAREN; }
")"             { return RIGHT_PAREN; }
"{"             { return LEFT_BRACE; }
"}"             { return RIGHT_BRACE; }
"["             { return LEFT_BRACKET; }
"]"             { return RIGHT_BRACKET; }
";"             { return SEMICOLON; }
","             { return COMMA; }

"extends"       {return EXTENDS;}

"return"        { return RETURN; }

"int"           { return INT; }
"boolean"       {return BOOLEAN;}

"System"        {return SYSTEM;}
"."             {return DOT;}
"out"           {return OUT;}
"println"       {return PRINTLN;}

"="             { return EQUAL_TO;}

"if"            { return IF; }
"else"          { return ELSE; }

"do"            {return DO;}
"while"         { return WHILE; }


"&&"            {return LOGICAL_AND;}
"||"            {return LOGICAL_OR;}
"!="            {return NEQ;}
"<="            {return LEQ;}
"+"             {return PLUS; }
"-"             {return MINUS; }
"*"             {return MUL; }
"/"             { return DIV; }
"length"        {return LENGTH;}


"true"          {return TRUE;}
"false"         {return FALSE;}
"this"          {return THIS;}
"new"           {return NEW;}
"!"             {return LOGICAL_NOT;}

"import"        {return IMPORT;}
"java.util.function.Function" {return UTIL;}
"Function"      {return FUNCTION;}

"#define"       {return HASH_DEFINE;}

"<"             {return '<';}
">"             {return '>';}



0|[1-9][0-9]*          { yylval.value = atoi(yytext);  return NUMBER; }
[a-zA-Z_][a-zA-Z0-9_]* { yylval.val= strdup(yytext); return IDENTIFIER; }

[ \t\n\r]           { /* ignore spaces */ }

.               { printf("// Failed to parse macrojava code.\n"); }



%%


int yywrap(void)
{
    return 1;
}