<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:lex="urn:lex:content:1"
                exclude-result-prefixes="lex">

    <xsl:output method="json" indent="yes"/>

    <xsl:template match="/lex:judgment">
        <xsl:variable name="header" select="lex:header"/>
        <xsl:variable name="paragraphs" select="lex:body/lex:section/lex:p"/>
        <xsl:sequence select="map {
            'content_id': string($header/lex:content_id),
            'title': string($header/lex:title),
            'court': string($header/lex:court),
            'jurisdiction': string($header/lex:jurisdiction),
            'decision_date': string($header/lex:decision_date),
            'citations': array {
                for $c in $header/lex:citations/lex:citation
                return map { 'type': string($c/@type), 'value': string($c) }
            },
            'parties': array {
                for $p in $header/lex:parties/lex:party
                return map { 'role': string($p/@role), 'name': string($p) }
            },
            'paragraphs': array {
                for $p in $paragraphs
                return map {
                    'id': string($p/@id),
                    'section': string($p/parent::lex:section/@type),
                    'text': normalize-space($p)
                }
            },
            'full_text': string-join($paragraphs ! normalize-space(.), ' ')
        }"/>
    </xsl:template>

</xsl:stylesheet>
