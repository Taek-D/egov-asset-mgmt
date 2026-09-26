<%--
  엑셀 일괄등록·내려받기 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>엑셀 일괄등록·내려받기</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="board">
	<h1>엑셀 일괄등록·내려받기</h1>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<c:if test="${not empty result and not result.ok}">
	<table class="board_list">
	<caption><c:out value="${uploadKind}"/> 일괄등록 — 틀린 줄</caption>
	<colgroup><col style="width:15%;"><col></colgroup>
	<thead>
	<tr>
		<th scope="col">행 번호</th>
		<th scope="col">이유</th>
	</tr>
	</thead>
	<tbody>
	<c:forEach items="${result.errors}" var="err">
	<tr>
		<td><c:out value="${err.rowNo}"/>행</td>
		<td class="left"><c:out value="${err.reason}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
	<br/>
	</c:if>

	<table class="wTable">
	<caption>일괄등록 (한 줄이라도 틀리면 아무것도 저장하지 않습니다. 1행은 머리글, .xlsx 또는 .xls)</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="itemFile">품목 일괄등록</label></th>
		<td class="left">
			<form name="itemUploadForm" action="<c:url value='/asset/excel/uploadItems.do'/>" method="post" enctype="multipart/form-data">
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			<input type="file" id="itemFile" name="file" title="품목 일괄등록 엑셀 파일 선택" accept=".xlsx,.xls" required="required"/>
			<input type="submit" class="s_btn" value="올리기" title="품목 일괄등록 파일 올리기"/>
			<span class="btn_s"><a href="<c:url value='/asset/excel/template.do'><c:param name='type' value='item'/></c:url>" title="품목 일괄등록 빈 양식 내려받기">빈 양식</a></span>
			</form>
			<p>열: 품목명*, 유형*(수량/개별), 분류코드, 단위코드, 최소 수량(비우면 0). 재고는 0으로 시작하며 입고로만 늘어납니다.</p>
		</td>
	</tr>
	<tr>
		<th scope="row"><label for="unitFile">자산 일괄등록</label></th>
		<td class="left">
			<form name="unitUploadForm" action="<c:url value='/asset/excel/uploadUnits.do'/>" method="post" enctype="multipart/form-data">
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			<input type="file" id="unitFile" name="file" title="자산 일괄등록 엑셀 파일 선택" accept=".xlsx,.xls" required="required"/>
			<input type="submit" class="s_btn" value="올리기" title="자산 일괄등록 파일 올리기"/>
			<span class="btn_s"><a href="<c:url value='/asset/excel/template.do'><c:param name='type' value='unit'/></c:url>" title="자산 일괄등록 빈 양식 내려받기">빈 양식</a></span>
			</form>
			<p>열: 품목코드*, 자산번호*, 비고. 한 줄이 입고 한 건입니다(사용 중인 개별 자산 품목만, 자산번호는 폐기 자산 포함 전체에서 유일).</p>
		</td>
	</tr>
	</tbody>
	</table>
	<br/>

	<table class="wTable">
	<caption>내려받기 (.xlsx)</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row">재고 현황</th>
		<td class="left"><span class="btn_s"><a href="<c:url value='/asset/excel/stock.do'/>" title="재고 현황 엑셀 내려받기">내려받기</a></span></td>
	</tr>
	<tr>
		<th scope="row">자산 대장</th>
		<td class="left"><span class="btn_s"><a href="<c:url value='/asset/excel/units.do'/>" title="자산 대장 엑셀 내려받기">내려받기</a></span></td>
	</tr>
	<tr>
		<th scope="row">신청 내역</th>
		<td class="left">
			<form name="reqDownloadForm" action="<c:url value='/asset/excel/requests.do'/>" method="get">
			<label for="fromDe">신청일</label>
			<input type="date" id="fromDe" name="fromDe" title="신청일 기간 시작" required="required" value="<c:out value='${fromDe}'/>"/>
			<label for="toDe">~</label>
			<input type="date" id="toDe" name="toDe" title="신청일 기간 끝" required="required" value="<c:out value='${toDe}'/>"/>
			<input type="submit" class="s_btn" value="내려받기" title="신청 내역 엑셀 내려받기"/>
			</form>
		</td>
	</tr>
	</tbody>
	</table>
</div>

</body>
</html>
