<%--
  지급 화면(자산담당자): 승인 품목 전부를 한 번에 지급. 개별 자산은 승인 수량만큼 보관 자산을 고른다.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>지급</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>지급</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<%@ include file="AssetReqHeader.jspf" %>

	<p style="clear:both; margin-top:10px;">승인된 품목 전부를 한 번에 지급합니다. 수량 비품은 승인 때 재고가 이미 줄었으므로 수량 변화가 없습니다.
	개별 자산은 승인 수량과 같은 수의 보관 자산을 골라야 하며, 고른 자산은 사용 상태가 되고 현재 사용자는 신청자가 됩니다. 지급한 뒤에는 취소할 수 없습니다.</p>

	<form name="deliverForm" action="<c:url value='/asset/deliver/submit.do'/>" method="post" onsubmit="return confirm('지급하시겠습니까? 지급 후에는 취소할 수 없습니다.');">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="reqNo" value="<c:out value='${req.reqNo}'/>"/>
	<table class="board_list">
	<caption>지급할 품목</caption>
	<colgroup><col style="width:25%;"><col style="width:10%;"><col style="width:10%;"><col></colgroup>
	<thead>
	<tr>
		<th scope="col">품목</th>
		<th scope="col">결과</th>
		<th scope="col">승인 수량</th>
		<th scope="col">지급 자산 선택</th>
	</tr>
	</thead>
	<tbody>
	<c:forEach items="${req.lines}" var="ln" varStatus="st">
	<tr>
		<td class="left"><c:out value="${ln.itemNm}"/></td>
		<td><c:out value="${lineSttusNm[ln.lineSttus]}"/></td>
		<td><c:out value="${ln.aprvQty}"/></td>
		<td class="left">
			<c:choose>
			<c:when test="${ln.lineSttus != 'APPROVED'}">지급하지 않음</c:when>
			<c:when test="${!ln.unit}">수량 비품(재고는 승인 때 차감됨)</c:when>
			<c:otherwise>
			<fieldset>
				<legend><c:out value="${ln.itemNm}"/>: 보관 자산 <c:out value="${ln.aprvQty}"/>대를 고르세요</legend>
				<c:set var="units" value="${req.storedUnits[ln.itemCode]}"/>
				<c:if test="${empty units}">보관 중인 자산이 없습니다.</c:if>
				<c:forEach items="${units}" var="u" varStatus="us">
				<input type="checkbox" id="pick${st.index}_${us.index}" name="pick_<c:out value='${ln.itemCode}'/>" value="<c:out value='${u.assetNo}'/>"/>
				<label for="pick${st.index}_${us.index}"><c:out value="${u.assetNo}"/></label>
				<c:if test="${not empty u.rm}">(<c:out value="${u.rm}"/>)</c:if><br/>
				</c:forEach>
			</fieldset>
			</c:otherwise>
			</c:choose>
		</td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
	<div class="btn">
		<input type="submit" class="s_submit" value="지급" title="승인 품목 전부 지급"/>
		<span class="btn_s"><a href="<c:url value='/asset/deliver/list.do'/>" title="지급 관리 목록으로 이동">목록</a></span>
		<span class="btn_s"><a href="<c:url value='/asset/req/detail.do'><c:param name='reqNo' value='${req.reqNo}'/></c:url>" title="신청 상세(지급취소)로 이동">상세·지급취소</a></span>
	</div>
	</form>
</div>

</body>
</html>
