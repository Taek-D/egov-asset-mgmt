<%--
  개별 자산 목록 (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>개별 자산 목록</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	document.unitListForm.pageIndex.value = pageNo;
	document.unitListForm.submit();
}
function fn_search() {
	document.unitListForm.pageIndex.value = 1;
	document.unitListForm.submit();
}
</script>
</head>
<body>

<form name="unitListForm" action="<c:url value='/asset/unit/list.do'/>" method="post" onsubmit="fn_search(); return false;">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
<div class="board">
	<h1>개별 자산 목록</h1>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<div class="search_box" title="검색 조건">
		<ul>
			<li>
				<label for="searchItemCode">품목</label>
				<select id="searchItemCode" name="searchItemCode" title="품목 선택">
					<option value="">전체</option>
					<c:forEach items="${itemList}" var="it">
					<option value="<c:out value='${it.itemCode}'/>" <c:if test="${searchVO.searchItemCode == it.itemCode}">selected="selected"</c:if>><c:out value="${it.itemNm}"/><c:if test="${it.useAt != 'Y'}"> (중지)</c:if></option>
					</c:forEach>
				</select>
			</li>
			<li>
				<label for="searchSttus">상태</label>
				<select id="searchSttus" name="searchSttus" title="상태 선택">
					<option value="">전체</option>
					<c:forEach items="${sttusList}" var="code">
					<option value="<c:out value='${code.code}'/>" <c:if test="${searchVO.searchSttus == code.code}">selected="selected"</c:if>><c:out value="${code.codeNm}"/></option>
					</c:forEach>
				</select>
			</li>
			<li>
				<label for="searchAssetNo">자산번호</label>
				<input class="s_input" id="searchAssetNo" name="searchAssetNo" type="text" size="25" maxlength="50" title="자산번호 입력(일부)" value="<c:out value='${searchVO.searchAssetNo}'/>">
				<input type="submit" class="s_btn" value="조회" title="조회 버튼" />
				<span class="btn_b"><a href="<c:url value='/asset/unit/form.do'/>" title="자산 등록 화면으로 이동">등록</a></span>
			</li>
		</ul>
	</div>

	<table class="board_list">
	<caption>개별 자산 목록</caption>
	<colgroup>
		<col style="width:6%;"><col style="width:18%;"><col><col style="width:9%;"><col style="width:14%;"><col style="width:22%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">번호</th>
		<th scope="col">자산번호</th>
		<th scope="col">품목</th>
		<th scope="col">상태</th>
		<th scope="col">현재 사용자</th>
		<th scope="col">비고</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr><td colspan="6">조회된 자산이 없습니다.</td></tr>
	</c:if>
	<c:forEach items="${resultList}" var="row" varStatus="status">
	<tr>
		<td><c:out value="${(searchVO.pageIndex - 1) * searchVO.pageUnit + status.count}"/></td>
		<td class="left"><a href="<c:url value='/asset/unit/detail.do'><c:param name='assetNo' value='${row.assetNo}'/></c:url>"><c:out value="${row.assetNo}"/></a></td>
		<td class="left"><c:out value="${row.itemNm}"/><c:if test="${row.useAt != 'Y'}"> (중지)</c:if></td>
		<td><c:out value="${sttusNm[row.sttus]}"/></td>
		<td><c:if test="${not empty row.userId}"><c:out value="${row.userNm}"/> (<c:out value="${row.userId}"/>)</c:if></td>
		<td class="left"><c:out value="${row.rm}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
</div>
<input name="pageIndex" type="hidden" value="<c:out value='${searchVO.pageIndex}'/>">
</form>

</body>
</html>
